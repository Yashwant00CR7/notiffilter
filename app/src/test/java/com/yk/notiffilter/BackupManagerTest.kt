package com.yk.notiffilter

import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.BackupData
import com.yk.notiffilter.data.BackupManager
import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun `roundtrip serialization preserves app rules, verdicts, and notification rules`() {
        val appRule = AppRule(
            packageName = "in.swiggy.android",
            appLabel = "Swiggy",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 123456789L,
        )

        val verdict = TemplateVerdict(
            fingerprint = "fp-12345",
            packageName = "in.swiggy.android",
            verdict = Verdict.HIDE,
            reason = "Spam offer",
            source = VerdictSource.USER,
            needsVerdict = false,
            score = 7.5,
            channelId = "promo_channel",
            sampleTitle = "FLAT 50% OFF",
            sampleText = "Use code SWIGGY50",
            updatedAt = 987654321L,
        )

        val globalRule = NotificationRule(
            packageName = null,
            pattern = "% off",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            enabled = true,
            description = "Percent off promo",
            createdAt = 111222333L,
        )

        val appScopedRule = NotificationRule(
            packageName = "in.swiggy.android",
            pattern = """marketing.*""",
            isRegex = true,
            matchTarget = MatchTarget.CHANNEL,
            verdict = Verdict.HIDE,
            enabled = true,
            description = "Swiggy marketing channel regex",
            createdAt = 444555666L,
        )

        val originalData = BackupData(
            version = 1,
            exportedAt = 555555555L,
            appRules = listOf(appRule),
            templateVerdicts = listOf(verdict),
            notificationRules = listOf(globalRule, appScopedRule),
        )

        val json = BackupManager.toJson(originalData)
        val restored = BackupManager.fromJson(json)

        assertEquals(1, restored.version)
        assertEquals(555555555L, restored.exportedAt)

        // Verify AppRule
        assertEquals(1, restored.appRules.size)
        val restoredApp = restored.appRules.first()
        assertEquals(appRule.packageName, restoredApp.packageName)
        assertEquals(appRule.appLabel, restoredApp.appLabel)
        assertEquals(appRule.mode, restoredApp.mode)
        assertEquals(appRule.enabled, restoredApp.enabled)
        assertEquals(appRule.repostKeepers, restoredApp.repostKeepers)

        // Verify TemplateVerdict
        assertEquals(1, restored.templateVerdicts.size)
        val restoredVerdict = restored.templateVerdicts.first()
        assertEquals(verdict.fingerprint, restoredVerdict.fingerprint)
        assertEquals(verdict.packageName, restoredVerdict.packageName)
        assertEquals(verdict.verdict, restoredVerdict.verdict)
        assertEquals(verdict.source, restoredVerdict.source)
        assertEquals(verdict.needsVerdict, restoredVerdict.needsVerdict)
        assertEquals(verdict.score, restoredVerdict.score, 0.001)
        assertEquals(verdict.channelId, restoredVerdict.channelId)
        assertEquals(verdict.sampleTitle, restoredVerdict.sampleTitle)
        assertEquals(verdict.sampleText, restoredVerdict.sampleText)

        // Verify NotificationRules
        assertEquals(2, restored.notificationRules.size)
        val restoredGlobal = restored.notificationRules[0]
        assertNull(restoredGlobal.packageName)
        assertEquals("% off", restoredGlobal.pattern)
        assertFalse(restoredGlobal.isRegex)
        assertEquals(MatchTarget.ANY, restoredGlobal.matchTarget)
        assertEquals(Verdict.HIDE, restoredGlobal.verdict)
        assertEquals("Percent off promo", restoredGlobal.description)

        val restoredScoped = restored.notificationRules[1]
        assertEquals("in.swiggy.android", restoredScoped.packageName)
        assertEquals("""marketing.*""", restoredScoped.pattern)
        assertTrue(restoredScoped.isRegex)
        assertEquals(MatchTarget.CHANNEL, restoredScoped.matchTarget)
    }

    @Test
    fun `empty backup deserializes gracefully`() {
        val emptyData = BackupData(
            version = 1,
            exportedAt = 1000L,
            appRules = emptyList(),
            templateVerdicts = emptyList(),
            notificationRules = emptyList(),
        )

        val json = BackupManager.toJson(emptyData)
        val restored = BackupManager.fromJson(json)

        assertTrue(restored.appRules.isEmpty())
        assertTrue(restored.templateVerdicts.isEmpty())
        assertTrue(restored.notificationRules.isEmpty())
    }
}
