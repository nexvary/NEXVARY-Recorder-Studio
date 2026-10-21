package com.nexvary.recorder.camera

import com.nexvary.recorder.delivery.DeliveryPolicy
import org.junit.Assert.*
import org.junit.Test

class CameraQualityTest {
    @Test fun defaultIsCompactAndUnknownKeysCannotEnableLargeFiles() {
        assertEquals(CameraQuality.COMPACT, CameraQuality.fromKey(null))
        assertEquals(CameraQuality.COMPACT, CameraQuality.fromKey("unknown"))
        CameraQuality.entries.forEach { assertEquals(it, CameraQuality.fromKey(it.key)) }
    }
    @Test fun resolutionRespectsPresetWithoutCroppingOrUpscaling() {
        val sizes = listOf(CameraQuality.FrameSize(1920,1080), CameraQuality.FrameSize(1280,720), CameraQuality.FrameSize(640,480), CameraQuality.FrameSize(320,240))
        assertEquals(CameraQuality.FrameSize(320,240), CameraQuality.COMPACT.chooseSize(sizes))
        assertEquals(CameraQuality.FrameSize(1280,720), CameraQuality.BALANCED.chooseSize(sizes))
        assertEquals(CameraQuality.FrameSize(1920,1080), CameraQuality.HIGH.chooseSize(sizes))
        assertEquals(CameraQuality.FrameSize(480,640), CameraQuality.COMPACT.chooseSize(listOf(CameraQuality.FrameSize(480,640))))
        assertEquals(CameraQuality.FrameSize(1280,720), CameraQuality.COMPACT.chooseSize(sizes.take(2)))
        assertNull(CameraQuality.COMPACT.chooseSize(emptyList()))
    }
    @Test fun fiveMinuteCompactWithAudioAndSafetyMarginFits25MiBMessageBudget() {
        val compact = CameraQuality.COMPACT
        assertTrue(compact.estimatedBytes(60_000, true) < 3_000_000)
        assertTrue(DeliveryPolicy.fits(compact.budgetBytes(300_000, true), 25))
        assertFalse(DeliveryPolicy.fits(CameraQuality.HIGH.budgetBytes(300_000, true), 25))
        assertFalse(DeliveryPolicy.fits(compact.budgetBytes(600_000, true), 25))
    }
    @Test fun estimatesIncludeAudioAndScaleSafelyAcrossMaximumDuration() {
        CameraQuality.entries.forEach {
            assertTrue(it.estimatedBytes(300_000,true) > it.estimatedBytes(300_000,false))
            assertTrue(it.budgetBytes(DeliveryPolicy.MAX_DURATION_MS,true) > it.estimatedBytes(DeliveryPolicy.MAX_DURATION_MS,true))
            assertTrue(DeliveryPolicy.messageBytes(it.estimatedBytes(300_000,true)) > it.estimatedBytes(300_000,true))
        }
    }
}
