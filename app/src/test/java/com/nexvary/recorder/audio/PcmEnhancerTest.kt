package com.nexvary.recorder.audio

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin

class PcmEnhancerTest {
    @Test fun producesValidMonoWavAndRetainsOriginalPcm() {
        val dir = Files.createTempDirectory("guardian-wav").toFile()
        try {
            val input = File(dir, "input.pcm"); val output = File(dir, "result.wav")
            val pcm = ByteBuffer.allocate(48_000 * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(48_000) { pcm.putShort((sin(it * 2 * Math.PI * 440 / 48_000) * 12_000).toInt().toShort()) }
            input.writeBytes(pcm.array())
            PcmEnhancer.enhance(input, output)
            val bytes = output.readBytes(); val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
            assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
            assertEquals(1, data.getShort(20).toInt()); assertEquals(1, data.getShort(22).toInt())
            assertEquals(48_000, data.getInt(24)); assertEquals(16, data.getShort(34).toInt())
            assertEquals(input.length().toInt(), data.getInt(40)); assertEquals(input.length() + 44, output.length())
            val peak = (44 until bytes.size step 2).maxOf { abs(data.getShort(it).toInt()) }
            assertTrue("Audio was not normalized into safe PCM range", peak in 29_000..30_200)
            assertArrayEquals(pcm.array(), input.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun silenceStaysSilentAndMalformedInputDoesNotOverwriteAnOutput() {
        val dir = Files.createTempDirectory("guardian-wav-silence").toFile()
        try {
            val input = File(dir, "silent.pcm").apply { writeBytes(ByteArray(40_000)) }
            val output = File(dir, "result.wav")
            PcmEnhancer.enhance(input, output)
            assertTrue(output.readBytes().drop(44).all { it == 0.toByte() })
            val before = output.readBytes()
            input.writeBytes(byteArrayOf(1))
            try { PcmEnhancer.enhance(input, output); fail("Odd PCM must be rejected") } catch (_: IllegalArgumentException) { }
            assertArrayEquals(before, output.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun longRecordingCanBeEnhancedWithA24MiBHeap() {
        val dir = Files.createTempDirectory("guardian-wav-memory").toFile()
        try {
            val input = File(dir, "long.pcm"); val output = File(dir, "long.wav")
            input.outputStream().buffered().use { stream -> val block = ByteArray(32 * 1024) { (it % 127).toByte() }; repeat(2048) { stream.write(block) } }
            val paths = listOf(PcmEnhancer::class.java, MemoryProbe::class.java, kotlin.Unit::class.java)
                .map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }.distinct().joinToString(File.pathSeparator)
            val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").absolutePath, "-Xmx24m", "-cp", paths, MemoryProbe::class.java.name, input.absolutePath, output.absolutePath).redirectErrorStream(true).start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Streaming audio enhancement timed out") }
            val diagnostic = process.inputStream.bufferedReader().readText()
            assertEquals(diagnostic, 0, process.exitValue())
            assertEquals(input.length() + 44, output.length())
        } finally { dir.deleteRecursively() }
    }
    object MemoryProbe {
        @JvmStatic fun main(args: Array<String>) { PcmEnhancer.enhance(File(args[0]), File(args[1])) }
    }
}
