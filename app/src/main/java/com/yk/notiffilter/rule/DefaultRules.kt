package com.yk.notiffilter.rule

import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.NotificationRuleDao
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource

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
     * Pre-configured active apps matching user session data.
     */
    val defaultAppRules: List<AppRule> = listOf(
        AppRule(
            packageName = "in.swiggy.android",
            appLabel = "Swiggy",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790056839024L,
        ),
        AppRule(
            packageName = "com.google.android.apps.nbu.paisa.user",
            appLabel = "GPay",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790056853745L,
        ),
        AppRule(
            packageName = "net.one97.paytm",
            appLabel = "Paytm",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790056885391L,
        ),
        AppRule(
            packageName = "com.icicidirect.idirectsuper",
            appLabel = "ICICI Direct",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790062279068L,
        ),
        AppRule(
            packageName = "com.csam.icici.bank.imobile",
            appLabel = "iMobile",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790062290492L,
        ),
        AppRule(
            packageName = "in.redbus.android",
            appLabel = "redBus",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790068106428L,
        ),
        AppRule(
            packageName = "in.mobility.cumta",
            appLabel = "Chennai One",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790166362492L,
        ),
        AppRule(
            packageName = "com.android.vending",
            appLabel = "Google Play Store",
            mode = FilterMode.ACTIVE,
            enabled = true,
            repostKeepers = true,
            createdAt = 1790166425259L,
        ),
    )

    /**
     * Ground-truth user verdicts from real sessions to train the on-device Bayesian classifier immediately.
     */
    val defaultVerdicts: List<TemplateVerdict> = listOf(
        TemplateVerdict(
            fingerprint = "857e59cddf74fcd27207f703ddf5bcdc10ee7943ef66725fc3b2b8b9735821bd",
            packageName = "in.swiggy.android",
            verdict = Verdict.HIDE,
            reason = "You decided",
            source = VerdictSource.USER,
            needsVerdict = false,
            score = 0.0,
            channelId = "marketing_notification_id",
            sampleTitle = "Meetings are hard enough. 😭",
            sampleText = "Make lunch easy with 70% OFF!",
            updatedAt = 1790062266478L,
        ),
        TemplateVerdict(
            fingerprint = "69a346890ca820c3df3812c3631179509a885aac801e6d72a6add0fa4eaac94e",
            packageName = "in.swiggy.android",
            verdict = Verdict.HIDE,
            reason = "You decided",
            source = VerdictSource.USER,
            needsVerdict = false,
            score = -22.532130774077398,
            channelId = "marketing_notification_id",
            sampleTitle = "Running on chai & hope? 🫠",
            sampleText = "Please get yourself something proper to eat.\n",
            updatedAt = 1790065386283L,
        ),
        TemplateVerdict(
            fingerprint = "da5afa912750d05be7d85f4ad80ee279bd0b350e1345c96911d02054b824cec4",
            packageName = "com.icicidirect.idirectsuper",
            verdict = Verdict.HIDE,
            reason = "You decided",
            source = VerdictSource.USER,
            needsVerdict = false,
            score = -7.595991857403515,
            channelId = "your channel id",
            sampleTitle = "Tune in to Nifty Ki Baat",
            sampleText = "",
            updatedAt = 1790065400476L,
        ),
        TemplateVerdict(
            fingerprint = "4c4f7d45b196e2c8c14d18d7a1c91b7c289af7d538798bef38072bb75f9b3b7b",
            packageName = "com.icicidirect.idirectsuper",
            verdict = Verdict.HIDE,
            reason = "Corrected by you",
            source = VerdictSource.USER,
            needsVerdict = false,
            score = -10.344231183514204,
            channelId = "your channel id",
            sampleTitle = "When Income Grows, Investments Should Too ",
            sampleText = "",
            updatedAt = 1790223428828L,
        ),
        TemplateVerdict(
            fingerprint = "89fbf0aa349d64f933a53b682fac89cfe239f5f33a243c0f864946a8a4b97418",
            packageName = "in.swiggy.android",
            verdict = Verdict.HIDE,
            reason = "Corrected by you",
            source = VerdictSource.USER,
            needsVerdict = false,
            score = -14.088446219525553,
            channelId = "moe_default_channel",
            sampleTitle = "Treats, with plot twists!",
            sampleText = "Explore naturally sweetened & unsweetened options. 💗🧁\n",
            updatedAt = 1790080109880L,
        ),
    )

    suspend fun preseedIfEmpty(dao: NotificationRuleDao) {
        if (dao.count() == 0) {
            dao.insertAll(rules)
        }
    }

    suspend fun preseedIfEmpty(db: com.yk.notiffilter.data.NotifFilterDatabase) {
        if (db.notificationRules().count() == 0) {
            db.notificationRules().insertAll(rules)
        } else {
            val existing = db.notificationRules().getAll()
            val existingKeys = existing.map { (it.packageName ?: "") + ":" + it.pattern + ":" + it.matchTarget.name }.toSet()
            val missing = rules.filter { rule ->
                val key = (rule.packageName ?: "") + ":" + rule.pattern + ":" + rule.matchTarget.name
                key !in existingKeys
            }
            if (missing.isNotEmpty()) {
                db.notificationRules().insertAll(missing)
            }
        }

        if (db.appRules().getAll().isEmpty()) {
            db.appRules().insertAll(defaultAppRules)
        }

        if (db.verdicts().getAll().isEmpty()) {
            db.verdicts().insertAll(defaultVerdicts)
        }
    }
}
