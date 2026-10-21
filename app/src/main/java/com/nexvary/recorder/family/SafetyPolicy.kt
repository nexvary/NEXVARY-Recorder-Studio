package com.nexvary.recorder.family

import kotlin.math.sqrt

enum class SafetyPhase { IDLE, ARMED, COUNTDOWN, SOS_ACTIVE, RESOLVED, CANCELLED }
data class SafetyDeadline(val wall: Long, val elapsed: Long, val boot: Int) {
    fun remaining(wallNow: Long, elapsedNow: Long, bootNow: Int): Long =
        (if (boot >= 0 && boot == bootNow) elapsed - elapsedNow else wall - wallNow).coerceAtLeast(0)
}
data class SafetyState(val id: String, val phase: SafetyPhase, val deadline: SafetyDeadline,
    val graceSeconds: Int = 60, val reason: String = "CHECK_IN", val test: Boolean = false)

object SafetyPolicy {
    fun validMinutes(value: Long) = value in 1..1440
    fun validGrace(seconds: Int) = seconds in 20..600
    fun evaluate(state: SafetyState, wall: Long, elapsed: Long, boot: Int): SafetyState = when {
        state.deadline.remaining(wall, elapsed, boot) > 0 -> state
        state.phase == SafetyPhase.ARMED -> state.copy(phase = SafetyPhase.COUNTDOWN,
            deadline = SafetyDeadline(wall + state.graceSeconds * 1000L, elapsed + state.graceSeconds * 1000L, boot))
        state.phase == SafetyPhase.COUNTDOWN -> state.copy(phase = SafetyPhase.SOS_ACTIVE)
        else -> state
    }
    fun batteryCrossings(previous: Int, current: Int, already: Set<Int>) =
        listOf(15,10,5).filter { current in 0..it && previous > it && it !in already }
    fun arrived(distanceMeters: Float, accuracyMeters: Float, ageMs: Long, radiusMeters: Float = 100f) =
        distanceMeters.isFinite() && distanceMeters >= 0 && accuracyMeters in 0f..100f &&
            ageMs in 0..120_000 && distanceMeters + accuracyMeters <= radiusMeters
}

/** Heuristic signal only. A dropped phone can trigger it; every event enters a cancellable countdown. */
class MotionDetector(private val sensitivity: Int) {
    private var last = -60_000L
    fun sample(x: Float, y: Float, z: Float, timeMs: Long): Float? {
        val g = sqrt(x*x+y*y+z*z) / 9.80665f
        val threshold = when (sensitivity) { 0 -> 5f; 2 -> 2.8f; else -> 3.8f }
        if (!g.isFinite() || g < threshold || timeMs - last < 60_000) return null
        last = timeMs
        return g
    }
}
