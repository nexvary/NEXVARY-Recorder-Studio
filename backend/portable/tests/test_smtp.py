import ssl
import smtplib
import socket
import subprocess
import threading
from unittest.mock import Mock,patch
import pytest
from providers import SmtpProvider, EmailAcceptanceUnknown


def provider():return SmtpProvider('alerts@example.com','smtp.example.com',587,'synthetic-user','synthetic-password')


def test_smtp_requires_verified_tls_before_auth_and_never_attaches_video():
    transport=Mock();transport.send_message.return_value={}
    with patch('smtplib.SMTP',return_value=transport):
        result=provider().send('parent@example.com','TEST ALERT — NO EMERGENCY','Private temporary link only')
    context=transport.starttls.call_args.kwargs['context']
    assert context.verify_mode==ssl.CERT_REQUIRED and context.check_hostname
    calls=[x[0] for x in transport.mock_calls]
    assert calls.index('starttls')<calls.index('login')<calls.index('send_message')
    message=transport.send_message.call_args.args[0]
    assert list(message.iter_attachments())==[]
    assert result==message['Message-ID'];transport.close.assert_called_once()


def test_smtp_lost_data_response_is_ambiguous_not_retryable():
    transport=Mock();transport.send_message.side_effect=smtplib.SMTPServerDisconnected()
    with patch('smtplib.SMTP',return_value=transport),pytest.raises(EmailAcceptanceUnknown):
        provider().send('parent@example.com','Emergency alert','Local QA only')


def test_smtp_rejects_plaintext_ports_and_header_injection():
    with pytest.raises(ValueError):SmtpProvider('alerts@example.com','smtp.example.com',25,'test','test')
    with patch('smtplib.SMTP') as transport,pytest.raises(ValueError):
        provider().send('parent@example.com','Subject\r\nBcc: attacker@example.com','test')
    transport.assert_not_called()


def test_real_starttls_rejects_self_signed_certificate_before_sending_credentials(tmp_path):
    key=tmp_path/'key.pem';cert=tmp_path/'cert.pem'
    subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cert),'-days','1','-subj','/CN=localhost'],check=True,capture_output=True)
    context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(cert,key)
    server=socket.socket();server.bind(('127.0.0.1',0));server.listen(1);server.settimeout(5)
    port=server.getsockname()[1];commands=[]
    def serve():
        conn=None
        try:
            conn,_=server.accept();conn.settimeout(5);file=conn.makefile('rwb',buffering=0)
            file.write(b'220 Local QA SMTP\r\n')
            while True:
                line=file.readline();commands.append(line)
                if line.upper().startswith(b'EHLO'):file.write(b'250-localhost\r\n250 STARTTLS\r\n')
                elif line.upper().startswith(b'STARTTLS'):
                    file.write(b'220 Begin TLS\r\n')
                    try:context.wrap_socket(conn,server_side=True).close()
                    except ssl.SSLError:pass
                    break
                else:break
        finally:
            if conn:conn.close()
            server.close()
    thread=threading.Thread(target=serve,daemon=True);thread.start()
    real=smtplib.SMTP
    def local(*args,**kwargs):return real('127.0.0.1',port,timeout=5)
    with patch('smtplib.SMTP',side_effect=local),pytest.raises(ssl.SSLCertVerificationError):
        provider().send('parent@example.com','Local certificate QA','Not sent')
    thread.join(6)
    assert not thread.is_alive()
    assert not any(x.upper().startswith((b'AUTH',b'MAIL',b'DATA')) for x in commands)
