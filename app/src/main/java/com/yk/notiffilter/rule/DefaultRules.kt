package com.yk.notiffilter.rule

import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.NotificationRuleDao
import com.yk.notiffilter.data.Verdict

object DefaultRules {

    /**
     * Sensible default rules for Indian & global consumer apps (food, grocery, rides, banking)
     * and transactional safety.
     */
    val rules: List<NotificationRule> = listOf(
        // ==========================================
        // GLOBAL WHITELIST (KEEP) - Transactional & Security Safety
        // ==========================================
        NotificationRule(
            packageName = null,
            pattern = """\b\d{4,8}\b.*(?:otp|verification|security code|secret code|one time password)""",
            isRegex = true,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "OTP & Verification Code regex",
        ),
        NotificationRule(
            packageName = null,
            pattern = "otp",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "OTP alert",
        ),
        NotificationRule(
            packageName = null,
            pattern = "verification code",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Verification code alert",
        ),
        NotificationRule(
            packageName = null,
            pattern = "out for delivery",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Order out for delivery",
        ),
        NotificationRule(
            packageName = null,
            pattern = "order confirmed",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Order confirmed",
        ),
        NotificationRule(
            packageName = null,
            pattern = "order placed",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Order placed",
        ),
        NotificationRule(
            packageName = null,
            pattern = "arriving in",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Courier arriving",
        ),
        NotificationRule(
            packageName = null,
            pattern = "on the way",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Delivery on the way",
        ),
        NotificationRule(
            packageName = null,
            pattern = "rider assigned",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Rider assigned",
        ),
        NotificationRule(
            packageName = null,
            pattern = "delivery partner",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Delivery partner update",
        ),
        NotificationRule(
            packageName = null,
            pattern = "order picked up",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Order picked up",
        ),
        NotificationRule(
            packageName = null,
            pattern = "delivered",
            isRegex = false,
            matchTarget = MatchTarget.TITLE,
            verdict = Verdict.KEEP,
            description = "Delivered title alert",
        ),
        NotificationRule(
            packageName = null,
            pattern = "driver arriving",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Cab driver arriving",
        ),
        NotificationRule(
            packageName = null,
            pattern = "driver has arrived",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Cab driver arrived",
        ),
        NotificationRule(
            packageName = null,
            pattern = "your ride is",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Active ride status",
        ),
        NotificationRule(
            packageName = null,
            pattern = "trip completed",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Ride trip completed",
        ),
        NotificationRule(
            packageName = null,
            pattern = "debited by",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Bank debited alert",
        ),
        NotificationRule(
            packageName = null,
            pattern = "debited for",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Bank debited alert",
        ),
        NotificationRule(
            packageName = null,
            pattern = "credited to",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Bank credited alert",
        ),
        NotificationRule(
            packageName = null,
            pattern = "credited with",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Bank credited alert",
        ),
        NotificationRule(
            packageName = null,
            pattern = "upi ref",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "UPI reference confirmation",
        ),

        // ==========================================
        // GLOBAL BLACKLIST (HIDE) - Marketing Spam Patterns
        // ==========================================
        NotificationRule(
            packageName = null,
            pattern = "% off",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "% off discount promotion",
        ),
        NotificationRule(
            packageName = null,
            pattern = "flat ₹",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Flat ₹ cash discount",
        ),
        NotificationRule(
            packageName = null,
            pattern = "flat rs",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Flat Rs cash discount",
        ),
        NotificationRule(
            packageName = null,
            pattern = "cashback",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Cashback promotional campaign",
        ),
        NotificationRule(
            packageName = null,
            pattern = "use code",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Coupon code offer",
        ),
        NotificationRule(
            packageName = null,
            pattern = "coupon code",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Coupon code offer",
        ),
        NotificationRule(
            packageName = null,
            pattern = "free delivery on orders",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Free delivery marketing",
        ),
        NotificationRule(
            packageName = null,
            pattern = "mega sale",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Sale marketing campaign",
        ),
        NotificationRule(
            packageName = null,
            pattern = "weekend treat",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Weekend treat marketing",
        ),
        NotificationRule(
            packageName = null,
            pattern = "craving",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Craving marketing prompt",
        ),
        NotificationRule(
            packageName = null,
            pattern = "exclusive deal",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Exclusive deal promotion",
        ),
        NotificationRule(
            packageName = null,
            pattern = "limited time deal",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Urgency marketing deal",
        ),
        NotificationRule(
            packageName = null,
            pattern = "hurry up",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Urgency marketing prompt",
        ),
        NotificationRule(
            packageName = null,
            pattern = "buy 1 get 1",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "BOGO offer promotion",
        ),
        NotificationRule(
            packageName = null,
            pattern = "bogo",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "BOGO offer promotion",
        ),

        // ==========================================
        // APP-SPECIFIC CHANNELS & PROMOTIONS
        // ==========================================
        NotificationRule(
            packageName = "in.swiggy.android",
            pattern = "marketing_notification_id",
            isRegex = false,
            matchTarget = MatchTarget.CHANNEL,
            verdict = Verdict.HIDE,
            description = "Swiggy marketing channel",
        ),
        NotificationRule(
            packageName = "com.application.zomato",
            pattern = "promotional",
            isRegex = false,
            matchTarget = MatchTarget.CHANNEL,
            verdict = Verdict.HIDE,
            description = "Zomato promotional channel",
        ),
        NotificationRule(
            packageName = "com.application.zomato",
            pattern = "cravings",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Zomato craving alert",
        ),
    )

    suspend fun preseedIfEmpty(dao: NotificationRuleDao) {
        if (dao.count() == 0) {
            dao.insertAll(rules)
        }
    }
}
