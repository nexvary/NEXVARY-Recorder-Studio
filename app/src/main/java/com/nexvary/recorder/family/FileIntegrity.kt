package com.nexvary.recorder.family

import java.io.File
import java.security.MessageDigest

object FileIntegrity {
    fun sha256(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun sha256(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(64*1024);while(true) { val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Offset acknowledgements must be exact and hash verified before advancing a checkpoint. */
class ProgressiveUploadEngine(private val transport: ProgressiveEvidenceUpload,private val chunkBytes: Int=256*1024) {
    init { require(chunkBytes in 1024..4*1024*1024) }
    fun resume(eventId: String,file: File,checkpoint: (Long) -> Unit): String {
        val digest=FileIntegrity.sha256(file)
        val session=transport.open(eventId,file.name,file.length(),digest)
        require(session.acceptedOffset in 0..file.length())
        var offset=session.acceptedOffset;checkpoint(offset)
        java.io.RandomAccessFile(file,"r").use { input ->
            input.seek(offset)
            while(offset<file.length()) {
                val bytes=ByteArray(minOf(chunkBytes.toLong(),file.length()-offset).toInt());input.readFully(bytes)
                val hash=FileIntegrity.sha256(bytes)
                val receipt=transport.send(session.uploadId,offset,bytes,hash)
                check(receipt.acceptedOffset==offset+bytes.size && receipt.sha256==hash)
                offset=receipt.acceptedOffset;checkpoint(offset)
            }
        }
        check(FileIntegrity.sha256(file)==digest)
        val link=transport.complete(session.uploadId,digest)
        val uri=java.net.URI(link);require(uri.scheme=="https" && uri.host!=null)
        return link
    }
}
