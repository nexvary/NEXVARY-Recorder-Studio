package com.nexvary.recorder.delivery

/** Persist exactly the profile that authenticated; failed tests keep the saved profile intact. */
class SmtpSetup(private val verify: (SmtpProfile) -> Unit, private val save: (SmtpProfile) -> Unit) {
    sealed interface Result {
        data object Saved : Result
        data object StorageFailed : Result
        data class Failed(val reason: SmtpFailure) : Result
    }
    fun testAndSave(profile: SmtpProfile): Result {
        try { verify(profile) } catch (e: Exception) { return Result.Failed(SmtpFailure.classify(e)) }
        return try { save(profile); Result.Saved } catch (_: Exception) { Result.StorageFailed }
    }
}
