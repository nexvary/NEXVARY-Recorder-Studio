package com.nexvary.recorder.family

import com.nexvary.recorder.billing.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SafetyPolicyTest {
    @Test fun wallClockChangesDoNotChangeSameBootDeadline() {
        val deadline=SafetyDeadline(1_000_000,50_000,3)
        assertEquals(10_000L,deadline.remaining(900_000,40_000,3))
        assertEquals(10_000L,deadline.remaining(1_100_000,40_000,3))
        assertEquals(100_000L,deadline.remaining(900_000,10,4))
    }
    @Test fun overdueRebootStillProvidesARealGracePromptBeforeAutomaticSos() {
        val armed=SafetyState("test",SafetyPhase.ARMED,SafetyDeadline(1000,1000,1),60)
        val prompted=SafetyPolicy.evaluate(armed,50_000,5_000,2)
        assertEquals(SafetyPhase.COUNTDOWN,prompted.phase)
        assertEquals(60_000L,prompted.deadline.remaining(50_000,5_000,2))
        assertEquals(SafetyPhase.SOS_ACTIVE,SafetyPolicy.evaluate(prompted,110_000,65_000,2).phase)
    }
    @Test fun resolvedCancelledAndActiveSosNeverRearmFromAnOldTick() {
        listOf(SafetyPhase.RESOLVED,SafetyPhase.CANCELLED,SafetyPhase.SOS_ACTIVE).forEach { phase ->
            val state=SafetyState("test",phase,SafetyDeadline(0,0,0))
            assertEquals(state,SafetyPolicy.evaluate(state,100,100,0))
        }
        assertTrue(SafetyPolicy.validMinutes(1));assertFalse(SafetyPolicy.validMinutes(0));assertFalse(SafetyPolicy.validMinutes(1441))
        assertFalse(SafetyPolicy.validGrace(19));assertTrue(SafetyPolicy.validGrace(20));assertFalse(SafetyPolicy.validGrace(601))
    }
    @Test fun batteryThresholdsAreDeduplicatedAndArrivalRequiresFreshAccurateFixes() {
        assertEquals(listOf(15,10),SafetyPolicy.batteryCrossings(20,9,emptySet()))
        assertEquals(emptyList<Int>(),SafetyPolicy.batteryCrossings(10,9,setOf(10)))
        assertTrue(SafetyPolicy.arrived(30f,20f,1000))
        assertFalse(SafetyPolicy.arrived(30f,101f,1000));assertFalse(SafetyPolicy.arrived(30f,20f,180_000))
        assertFalse(SafetyPolicy.arrived(95f,20f,1000))
    }
    @Test fun arrivalNeverAutomaticallyResolvesAnActiveOrManualSos() {
        val journey=SafetyState("journey",SafetyPhase.ARMED,SafetyDeadline(0,0,0),reason="JOURNEY_TIMEOUT")
        assertTrue(SafetyPolicy.arrivalAllowed(journey));assertTrue(SafetyPolicy.arrivalAllowed(journey.copy(phase=SafetyPhase.COUNTDOWN)))
        assertFalse(SafetyPolicy.arrivalAllowed(journey.copy(phase=SafetyPhase.SOS_ACTIVE)))
        assertFalse(SafetyPolicy.arrivalAllowed(journey.copy(reason="SOS",phase=SafetyPhase.COUNTDOWN)))
        assertFalse(SafetyPolicy.arrivalAllowed(null))
    }
    @Test fun motionSignalsAreHeuristicAndHaveACooldown() {
        val detector=MotionDetector(1)
        assertNull(detector.sample(0f,0f,9.8f,0))
        assertNotNull(detector.sample(0f,0f,49f,100))
        assertNull(detector.sample(0f,0f,49f,1000))
        assertNotNull(detector.sample(0f,0f,49f,61_000))
    }
    @Test fun entitlementStatesFailClosedForPendingHoldExpiryAndUnverified() {
        SubscriptionState.entries.forEach { state ->
            val value=VerifiedEntitlement(state,20_000,1000)
            assertEquals(state in listOf(SubscriptionState.ACTIVE,SubscriptionState.GRACE_PERIOD,SubscriptionState.CANCELLED_ACTIVE),EntitlementPolicy.premium(value,5000))
            assertFalse(EntitlementPolicy.premium(value,500))
            assertFalse(EntitlementPolicy.premium(value,21_000))
        }
        assertFalse(EntitlementPolicy.premium(null,5000));assertTrue(SafetyFeature.BASIC_SOS.basic)
    }
    @Test fun hashesMatchKnownVectorAndDetectMutation() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",FileIntegrity.sha256("abc".toByteArray()))
        val file=File.createTempFile("integrity-test",".bin")
        try { file.writeText("abc");val before=FileIntegrity.sha256(file);file.appendText("d");assertNotEquals(before,FileIntegrity.sha256(file)) } finally { file.delete() }
    }
    @Test fun chunkEngineResumesFromAcknowledgedOffsetWithoutLosingOrDuplicatingBytes() {
        val source=File.createTempFile("upload-test",".bin").apply { writeBytes(ByteArray(700_001) { (it%251).toByte() }) }
        // TEST ONLY: no fake uploader is supplied to the application.
        val accepted=java.io.ByteArrayOutputStream();var failOnce=true
        val fake=object: ProgressiveEvidenceUpload {
            override fun open(eventId: String,fileName: String,totalBytes: Long,sha256: String)=ProgressiveEvidenceUpload.Session("local-test",accepted.size().toLong())
            override fun send(uploadId: String,offset: Long,bytes: ByteArray,sha256: String): ProgressiveEvidenceUpload.Receipt {
                if(offset>0 && failOnce) { failOnce=false;throw java.io.IOException("Local simulated disconnect") }
                assertEquals(accepted.size().toLong(),offset);accepted.write(bytes)
                return ProgressiveEvidenceUpload.Receipt(accepted.size().toLong(),sha256)
            }
            override fun complete(uploadId: String,sha256: String)="https://example.invalid/test-only"
        }
        try {
            assertTrue(runCatching { ProgressiveUploadEngine(fake).resume("test",source) {} }.isFailure)
            assertEquals("https://example.invalid/test-only",ProgressiveUploadEngine(fake).resume("test",source) {})
            assertArrayEquals(source.readBytes(),accepted.toByteArray())
        } finally { source.delete() }
    }
}
