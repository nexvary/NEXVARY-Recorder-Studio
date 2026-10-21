package com.nexvary.recorder.guardian

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedInputTest {
    @Test fun exactLimitAndEmptyResponsesAreRead() {
        val bytes=ByteArray(256*1024) { (it%251).toByte() }
        assertArrayEquals(bytes,BoundedInput.read(ByteArrayInputStream(bytes),bytes.size))
        assertArrayEquals(byteArrayOf(),BoundedInput.read(ByteArrayInputStream(byteArrayOf()),10))
    }
    @Test fun oversizedResponsesAreRejectedInsteadOfTruncated() {
        assertThrows(IllegalArgumentException::class.java) { BoundedInput.read(ByteArrayInputStream(ByteArray(101)),100) }
    }
    @Test fun shortReadsPreserveAllBytes() {
        val source=object: ByteArrayInputStream("Guardian response".toByteArray()) {
            override fun read(b: ByteArray,off: Int,len: Int)=super.read(b,off,minOf(len,2))
        }
        assertEquals("Guardian response",String(BoundedInput.read(source,100)))
    }
}
