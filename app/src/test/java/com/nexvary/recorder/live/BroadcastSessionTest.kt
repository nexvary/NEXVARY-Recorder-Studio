package com.nexvary.recorder.live

import org.junit.Assert.*
import org.junit.Test

class BroadcastSessionTest {
    @Test fun stopDuringPreparationPreventsAnyLaterNetworkStart() {
        val session = BroadcastSession<String>()
        val token = session.begin()!!
        assertNotNull(session.stop())
        var starts = 0
        assertFalse(session.attach(token, "late encoder") { starts++ })
        assertEquals(0, starts)
    }
    @Test fun staleCallbackCannotStopANewerBroadcast() {
        val session = BroadcastSession<String>()
        val old = session.begin()!!
        session.stop()
        val current = session.begin()!!
        assertTrue(session.attach(current, "new encoder") {})
        assertNull(session.stop(old))
        assertTrue(session.current(current))
        assertEquals("new encoder", session.stop(current)!!.resource)
        assertNull(session.stop(current))
    }
    @Test fun repeatedStartCannotCreateTwoActiveStreams() {
        val session = BroadcastSession<String>()
        val token = session.begin()!!
        assertNull(session.begin())
        assertTrue(session.attach(token, "encoder") {})
        assertEquals("encoder", session.stop()!!.resource)
        assertNotNull(session.begin())
    }
}
