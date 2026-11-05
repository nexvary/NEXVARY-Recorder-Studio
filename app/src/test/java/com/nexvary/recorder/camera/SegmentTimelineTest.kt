package com.nexvary.recorder.camera

import org.junit.Assert.*
import org.junit.Test

class SegmentTimelineTest {
    @Test fun fifteenMinutesCreatesFifteenIndependentBoundariesWithoutEndingSession() {
        val timeline = SegmentTimeline(60)
        var boundaries = 0
        for (second in 0 until 900) {
            if (timeline.acceptVideo(second * 1_000_000L, second % 2 == 0)) boundaries++
        }
        assertEquals(15, boundaries); assertEquals(15, timeline.index)
        assertFalse(timeline.acceptVideo(900_000_000, false))
        assertTrue(timeline.acceptVideo(902_000_000, true))
        assertEquals(16, timeline.index)
    }
    @Test fun durationIsMinimumAndEachClipRequiresSyncFrame() {
        val timeline = SegmentTimeline(30)
        assertFalse(timeline.acceptVideo(0, false)); assertEquals(0, timeline.index)
        assertTrue(timeline.acceptVideo(100_000, true))
        assertFalse(timeline.acceptVideo(30_099_999, true))
        assertFalse(timeline.acceptVideo(31_000_000, false))
        assertTrue(timeline.requestSync(31_000_000))
        assertTrue(timeline.acceptVideo(31_200_000, true))
    }
    @Test fun restartKeepsStableUniqueDeliveryKeys() {
        val session = java.util.UUID.randomUUID().toString()
        assertEquals(SegmentTimeline.jobId(session, 1), SegmentTimeline.jobId(session, 1))
        assertNotEquals(SegmentTimeline.jobId(session, 1), SegmentTimeline.jobId(session, 2))
        assertNotEquals(SegmentTimeline.jobId(session, 1), SegmentTimeline.jobId(java.util.UUID.randomUUID().toString(), 1))
    }
    @Test fun storageReserveIncludesSendCopyAndFloor() {
        assertFalse(SegmentTimeline.enoughSpace(0, 100))
        assertFalse(SegmentTimeline.enoughSpace(64L * 1024 * 1024, 100))
        assertFalse(SegmentTimeline.enoughSpace(100_000_000, 200_000_000))
        assertTrue(SegmentTimeline.enoughSpace(300_000_000, 200_000_000))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedDuration() { SegmentTimeline(1) }
}
