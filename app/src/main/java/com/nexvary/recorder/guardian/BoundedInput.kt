package com.nexvary.recorder.guardian

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** API 29 compatible response bound; never silently accepts a truncated JSON body. */
object BoundedInput {
    fun read(input: InputStream,limit: Int): ByteArray {
        require(limit>0)
        val output=ByteArrayOutputStream(minOf(limit,8192))
        val buffer=ByteArray(minOf(limit,8192))
        while(true) {
            val count=input.read(buffer)
            if(count<0) break
            if(count==0) continue
            require(output.size()<=limit-count) { "Response exceeds allowed size" }
            output.write(buffer,0,count)
        }
        return output.toByteArray()
    }
}
