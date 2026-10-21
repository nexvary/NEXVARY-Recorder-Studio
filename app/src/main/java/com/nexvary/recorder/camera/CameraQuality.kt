package com.nexvary.recorder.camera

import com.nexvary.recorder.delivery.DeliveryPolicy

/** Targets, not guaranteed file sizes: Android encoders may adjust the requested bitrate. */
enum class CameraQuality(val key: String, val longEdge: Int, val shortEdge: Int, val fps: Int,
    val videoBitrate: Int, val audioBitrate: Int, val audioSampleRate: Int) {
    COMPACT("compact", 640, 480, 15, 320_000, 32_000, 16_000),
    BALANCED("balanced", 1280, 720, 24, 1_200_000, 64_000, 32_000),
    HIGH("high", 1920, 1080, 30, 5_000_000, 128_000, 44_100);

    data class FrameSize(val width: Int, val height: Int) {
        val area get() = width.toLong() * height
        val longEdge get() = maxOf(width, height)
        val shortEdge get() = minOf(width, height)
    }
    fun chooseSize(supported: List<FrameSize>): FrameSize? {
        val sizes = supported.filter { it.width > 0 && it.height > 0 }
        return sizes.filter { it.longEdge <= longEdge && it.shortEdge <= shortEdge }.maxByOrNull { it.area }
            ?: sizes.minByOrNull { it.area }
    }
    /** Includes a small MP4 container allowance, before MIME/base64 expansion. */
    fun estimatedBytes(durationMs: Long, audio: Boolean): Long {
        require(durationMs in 0..DeliveryPolicy.MAX_DURATION_MS)
        return (videoBitrate.toLong() + if (audio) audioBitrate else 0) * durationMs / 8_000 + 65_536
    }
    fun budgetBytes(durationMs: Long, audio: Boolean) = estimatedBytes(durationMs, audio) * 125 / 100
    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: COMPACT
    }
}
