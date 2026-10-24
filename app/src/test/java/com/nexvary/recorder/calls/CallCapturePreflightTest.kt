package com.nexvary.recorder.calls
import org.junit.Assert.*
import org.junit.Test
class CallCapturePreflightTest {
    @Test fun managerPresenceAndServiceAloneDoNotAuthorizeIntegration() {
        assertFalse(CallCapturePreflight.Result(true, true, false, true).readyForIntegration)
        assertFalse(CallCapturePreflight.Result(true, false, true, true).readyForIntegration)
        assertFalse(CallCapturePreflight.Result(false, true, true, true).readyForIntegration)
        assertFalse(CallCapturePreflight.Result(true, true, true, false).readyForIntegration)
        assertTrue(CallCapturePreflight.Result(true, true, true, true).readyForIntegration)
    }
}
