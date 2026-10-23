package com.nexvary.recorder.live

/** Preparation is asynchronous. A stopped or superseded preparation must never start. */
internal class BroadcastSession<T> {
    private var generation = 0L
    private var active = false
    private var resource: T? = null
    data class Stopped<T>(val resource: T?)
    @Synchronized fun begin(): Long? {
        if (active) return null
        active = true
        generation++
        return generation
    }
    @Synchronized fun current(token: Long): Boolean = active && generation == token
    @Synchronized fun attach(token: Long, value: T, start: () -> Unit): Boolean {
        if (!current(token)) return false
        resource = value
        start()
        return true
    }
    @Synchronized fun stop(token: Long? = null): Stopped<T>? {
        if (!active || (token != null && generation != token)) return null
        active = false
        generation++
        return Stopped(resource).also { resource = null }
    }
}
