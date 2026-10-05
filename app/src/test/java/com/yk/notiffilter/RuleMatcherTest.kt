package com.yk.notiffilter

import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.rule.DefaultRules
import com.yk.notiffilter.rule.RuleMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    @Test
    fun `whitelist KEEP rule takes priority over blacklist HIDE rule`() {
        val rules = listOf(
            NotificationRule(
                pattern = "out for delivery",
                verdict = Verdict.KEEP,
                description = "Delivery status",
            ),
            NotificationRule(
                pattern = "% off",
                verdict = Verdict.HIDE,
                description = "Discount promo",
            ),
        )

        // Notification contains BOTH "out for delivery" and "20% off"
        val result = RuleMatcher.match(
            rules = rules,
            packageName = "in.swiggy.android",
            channelId = "orders",
            title = "Out for delivery!",
            text = "Your biryani is on the way. Rate your last order for 20% off!",
        )

        assertTrue(result.matched)
        assertEquals(Verdict.KEEP, result.verdict)
        assertTrue(result.reason.contains("Delivery status"))
    }

    @Test
    fun `blacklist rule catches promotional copy with case-insensitivity`() {
        val rules = listOf(
            NotificationRule(
                pattern = "% off",
                verdict = Verdict.HIDE,
                description = "Percent off",
            ),
            NotificationRule(
                pattern = "flat ₹",
                verdict = Verdict.HIDE,
                description = "Flat cash",
            ),
        )

        val result1 = RuleMatcher.match(
            rules = rules,
            packageName = "in.swiggy.android",
            channelId = null,
            title = "MEGA SALE",
            text = "Grab FLAT 50% OFF on all burgers today!",
        )
        assertTrue(result1.matched)
        assertEquals(Verdict.HIDE, result1.verdict)

        val result2 = RuleMatcher.match(
            rules = rules,
            packageName = "com.application.zomato",
            channelId = null,
            title = "Weekend Treat",
            text = "Enjoy Flat ₹100 off on dining out!",
        )
        assertTrue(result2.matched)
        assertEquals(Verdict.HIDE, result2.verdict)
    }

    @Test
    fun `regex rule matches complex OTP and verification code strings`() {
        val rules = DefaultRules.rules

        val result = RuleMatcher.match(
            rules = rules,
            packageName = "com.bank.app",
            channelId = "security",
            title = "Your OTP is 482910",
            text = "Use one time password 482910 to approve payment of Rs 500.",
        )

        assertTrue(result.matched)
        assertEquals(Verdict.KEEP, result.verdict)
    }

    @Test
    fun `channel-targeted rule matches channel ID correctly`() {
        val rules = listOf(
            NotificationRule(
                packageName = "in.swiggy.android",
                pattern = "marketing_notification_id",
                matchTarget = MatchTarget.CHANNEL,
                verdict = Verdict.HIDE,
                description = "Swiggy promo channel",
            ),
        )

        val matched = RuleMatcher.match(
            rules = rules,
            packageName = "in.swiggy.android",
            channelId = "marketing_notification_id",
            title = "Lunch time",
            text = "Order now",
        )
        assertTrue(matched.matched)
        assertEquals(Verdict.HIDE, matched.verdict)

        val unmatched = RuleMatcher.match(
            rules = rules,
            packageName = "in.swiggy.android",
            channelId = "order_status",
            title = "Lunch time",
            text = "Order now",
        )
        assertFalse(unmatched.matched)
    }

    @Test
    fun `app-scoped rule does not apply to other packages`() {
        val rules = listOf(
            NotificationRule(
                packageName = "in.swiggy.android",
                pattern = "cravings",
                verdict = Verdict.HIDE,
            ),
        )

        val otherApp = RuleMatcher.match(
            rules = rules,
            packageName = "com.application.zomato",
            channelId = null,
            title = "Midnight cravings?",
            text = "Order now",
        )
        assertFalse(otherApp.matched)

        val swiggy = RuleMatcher.match(
            rules = rules,
            packageName = "in.swiggy.android",
            channelId = null,
            title = "Midnight cravings?",
            text = "Order now",
        )
        assertTrue(swiggy.matched)
        assertEquals(Verdict.HIDE, swiggy.verdict)
    }

    @Test
    fun `disabled rule is ignored`() {
        val rules = listOf(
            NotificationRule(
                pattern = "cashback",
                verdict = Verdict.HIDE,
                enabled = false,
            ),
        )

        val result = RuleMatcher.match(
            rules = rules,
            packageName = "com.wallet.app",
            channelId = null,
            title = "Win Cashback",
            text = "Earn up to Rs 50",
        )
        assertFalse(result.matched)
    }

    @Test
    fun `default rules preset contains essential delivery and spam rules`() {
        val rules = DefaultRules.rules

        // Delivery check
        val delivery = RuleMatcher.match(
            rules = rules,
            packageName = "com.blinkit.app",
            channelId = null,
            title = "Order picked up",
            text = "Your delivery partner is on the way with your order",
        )
        assertTrue(delivery.matched)
        assertEquals(Verdict.KEEP, delivery.verdict)

        // Spam check
        val spam = RuleMatcher.match(
            rules = rules,
            packageName = "com.zepto.app",
            channelId = null,
            title = "Hungry? Craving something sweet?",
            text = "Get 40% off on ice creams today!",
        )
        assertTrue(spam.matched)
        assertEquals(Verdict.HIDE, spam.verdict)
    }
}
