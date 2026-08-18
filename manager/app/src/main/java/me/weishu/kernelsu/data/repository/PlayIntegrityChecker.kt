package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp

/**
 * Play Integrity probe. F1a ships a stub; F1a+ wires the Play library + cloud project.
 */
data class PlayIntegrityResult(
    val available: Boolean,
    val summary: String,
    val raw: String = "",
)

interface PlayIntegrityChecker {
    suspend fun check(): PlayIntegrityResult
}

class StubPlayIntegrityChecker : PlayIntegrityChecker {
    override suspend fun check(): PlayIntegrityResult = PlayIntegrityResult(
        available = false,
        summary = ksuApp.getString(R.string.env_check_pi_not_configured),
        raw = "stub",
    )
}
