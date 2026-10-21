from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch
import pytest
from django.db import connection,close_old_connections
from guardian_hosted import auth,emergencies
from guardian_hosted.models import Account
from guardian_hosted.core import usage
from guardian_hosted.queue import drain
from policy import Rejected
from uuid import uuid4

pytestmark=pytest.mark.django_db(transaction=True)


def run_separate(action):
    close_old_connections()
    try:return action()
    finally:close_old_connections()


def test_mysql_concurrent_sos_requests_merge_to_one_reservation(account,connected):
    if connection.vendor!='mysql':pytest.skip('Production lock behavior is exercised by the MySQL CI job')
    def reserve():
        owner=Account.objects.get(pk=account.id)
        return emergencies.reserve(owner,{'eventId':str(uuid4()),'payload':{}})['eventId']
    with ThreadPoolExecutor(max_workers=6) as pool:
        values=list(pool.map(lambda _:run_separate(reserve),range(6)))
    assert len(set(values))==1 and usage(account)['used']==1


def test_mysql_concurrent_refresh_has_one_winner(account):
    if connection.vendor!='mysql':pytest.skip('Production lock behavior is exercised by the MySQL CI job')
    values=auth.login({'email':account.email,'password':'GuardianPass!738'})
    def refresh():
        try:auth.refresh({'refreshToken':values['RefreshToken']});return 'OK'
        except Rejected:return 'REJECTED'
    with ThreadPoolExecutor(max_workers=2) as pool:
        result=list(pool.map(lambda _:run_separate(refresh),range(2)))
    assert sorted(result)==['OK','REJECTED']
