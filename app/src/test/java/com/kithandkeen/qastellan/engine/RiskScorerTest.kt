package com.kithandkeen.qastellan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskScorerTest {
    private fun app(
        installer: String? = "com.android.vending",
        perms: Set<String> = emptySet(),
        accessibility: Boolean = false,
        admin: Boolean = false,
        launcher: Boolean = true,
        system: Boolean = false,
        pkg: String = "com.example.app",
        signer: String? = "aa",
    ) = AppFacts(pkg, "Example", installer, system, perms, accessibility, admin, launcher, signer)

    @Test
    fun `ordinary store app is low risk`() {
        val report = RiskScorer().score(app(perms = setOf("android.permission.CAMERA")))
        assertEquals(RiskReport.Level.LOW, report.level)
    }

    @Test
    fun `sideloaded banking trojan pattern is high risk with plain reasons`() {
        val report = RiskScorer().score(
            app(
                installer = "com.android.chrome",
                perms = setOf(RiskScorer.OVERLAY, "android.permission.READ_SMS"),
                accessibility = true,
            ),
        )
        assertEquals(RiskReport.Level.HIGH, report.level)
        assertTrue(report.reasons.any { "outside an app store" in it })
        assertTrue(report.reasons.any { "text messages" in it })
    }

    @Test
    fun `system apps are never flagged by behaviour`() {
        val report = RiskScorer().score(app(system = true, accessibility = true, admin = true, launcher = false))
        assertEquals(0, report.score)
    }

    @Test
    fun `fake copy of a known app is high risk`() {
        val scorer = RiskScorer(knownSigners = mapOf("com.whatsup.app" to "ff"))
        val report = scorer.score(app(pkg = "com.whatsup.app", signer = "00"))
        assertEquals(RiskReport.Level.HIGH, report.level)
    }

    @Test
    fun `score is capped at 100`() {
        val report = RiskScorer().score(
            app(
                installer = null,
                perms = setOf(RiskScorer.OVERLAY, RiskScorer.BIND_NOTIFICATIONS, RiskScorer.INSTALL_PACKAGES, "android.permission.SEND_SMS"),
                accessibility = true,
                admin = true,
                launcher = false,
            ),
        )
        assertEquals(100, report.score)
    }
}
