package com.yk.notiffilter.rule

import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.settings.AppSettings

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
            pattern = """cashback\s(?:of\s)?[^.]{0,20}(?:credited|received|added)""",
            isRegex = true,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Cashback actually credited",
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
        // Narrowed deliberately. A bare "cashback" also matched "₹50 cashback
        // credited to your account", so a real payment confirmation was hidden as
        // marketing. These forms only occur in the offer, never in the receipt.
        NotificationRule(
            packageName = null,
            pattern = """(?:earn|get|win|upto|up to|flat)\s[^.]{0,20}cashback""",
            isRegex = true,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Cashback offer promotion",
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
        // Swiggy
        NotificationRule(
            packageName = "in.swiggy.android",
            pattern = "marketing_notification_id",
            isRegex = false,
            matchTarget = MatchTarget.CHANNEL,
            verdict = Verdict.HIDE,
            description = "Swiggy marketing channel",
        ),
        NotificationRule(
            packageName = "in.swiggy.android",
            pattern = "moe_default_channel",
            isRegex = false,
            matchTarget = MatchTarget.CHANNEL,
            verdict = Verdict.HIDE,
            description = "Swiggy MoEngage marketing channel",
        ),
        NotificationRule(
            packageName = "in.swiggy.android",
            pattern = "chai & hope",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Swiggy conversational marketing",
        ),
        NotificationRule(
            packageName = "in.swiggy.android",
            pattern = "plot twists",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Swiggy conversational marketing",
        ),

        // ICICI Direct
        NotificationRule(
            packageName = "com.icicidirect.idirectsuper",
            pattern = "your channel id",
            isRegex = false,
            matchTarget = MatchTarget.CHANNEL,
            verdict = Verdict.HIDE,
            description = "ICICI Direct promo/webinar channel",
        ),
        NotificationRule(
            packageName = "com.icicidirect.idirectsuper",
            pattern = "nifty ki baat",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "ICICI Direct webinar marketing",
        ),
        NotificationRule(
            packageName = "com.icicidirect.idirectsuper",
            pattern = "investments should too",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "ICICI Direct investment marketing",
        ),

        // redBus
        NotificationRule(
            packageName = "in.redbus.android",
            pattern = "boarding point",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "redBus boarding point alert",
        ),
        NotificationRule(
            packageName = "in.redbus.android",
            pattern = "bus ticket",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "redBus ticket confirmation",
        ),

        // Chennai One
        NotificationRule(
            packageName = "in.mobility.cumta",
            pattern = "ticket",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Chennai One ticket confirmation",
        ),
        NotificationRule(
            packageName = "in.mobility.cumta",
            pattern = "qr code",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.KEEP,
            description = "Chennai One QR pass",
        ),

        // iMobile (ICICI Bank)
        NotificationRule(
            packageName = "com.csam.icici.bank.imobile",
            pattern = "pre-approved loan",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "iMobile loan promotional alert",
        ),

        // GPay & Paytm promos
        NotificationRule(
            packageName = "com.google.android.apps.nbu.paisa.user",
            pattern = "scratch card",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "GPay scratch card marketing",
        ),
        NotificationRule(
            packageName = "net.one97.paytm",
            pattern = "cashback points",
            isRegex = false,
            matchTarget = MatchTarget.ANY,
            verdict = Verdict.HIDE,
            description = "Paytm cashback points marketing",
        ),

        // Zomato
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

    /**
     * Seeds the shipped rule set exactly once.
     *
     * Deliberately one-shot. An earlier version back-filled any "missing" default on
     * every launch, which meant a rule the user deleted in the UI reappeared the next
     * time the app started — the rule list was not actually theirs to edit.
     *
     * Nothing else is seeded. No app is opted in and no verdict is pre-labelled: the
     * user chooses which apps to watch, and every app starts in [FilterMode.OBSERVE].
     */
    suspend fun preseedIfEmpty(db: NotifFilterDatabase, settings: AppSettings) {
        if (settings.defaultsSeeded) return
        if (db.notificationRules().count() == 0) {
            db.notificationRules().insertAll(rules)
        }
        settings.defaultsSeeded = true
    }
}
