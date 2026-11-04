package com.nexvary.recorder.camera

/** Presentation-time boundaries; a new MP4 always starts with an independently decodable frame. */
class SegmentTimeline(val seconds: Int) {
    init { require(seconds in DURATIONS) }
    var startUs: Long = -1L
        private set
    var index: Int = 0
        private set
    fun acceptVideo(ptsUs: Long, keyFrame: Boolean): Boolean {
        require(ptsUs >= 0)
        if (startUs < 0) {
            if (!keyFrame) return false
            startUs = ptsUs; index = 1; return true
        }
        if (keyFrame && ptsUs - startUs >= seconds * 1_000_000L) {
            startUs = ptsUs; index++; return true
        }
        return false
    }
    fun requestSync(ptsUs: Long) = startUs >= 0 && ptsUs - startUs >= seconds * 1_000_000L
    companion object {
        val DURATIONS = listOf(30, 60, 120, 300)
        fun jobId(session: String, index: Int): String {
            java.util.UUID.fromString(session); require(index > 0)
            return java.util.UUID.nameUUIDFromBytes("continuous:$session:$index".toByteArray(Charsets.UTF_8)).toString()
        }
        fun enoughSpace(available: Long, reserve: Long) = available > maxOf(64L * 1024 * 1024, reserve)
    }
}
