package com.yk.notiffilter.rule

import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.Verdict

data class RuleMatchResult(
    val matched: Boolean,
    val verdict: Verdict,
    val rule: NotificationRule?,
    val reason: String,
) {
    companion object {
        val NO_MATCH = RuleMatchResult(
            matched = false,
            verdict = Verdict.KEEP,
            rule = null,
            reason = "No rule match",
        )
    }
}

object RuleMatcher {

    /**
     * Evaluates incoming notification content against active rules.
     *
     * Invariant: Whitelist (KEEP) rules are evaluated first. If any Whitelist rule matches,
     * it immediately returns KEEP, preventing any Blacklist rule or AI from hiding critical alerts.
     *
     * If no Whitelist rule matches, Blacklist (HIDE) rules are evaluated.
     */
    fun match(
        rules: List<NotificationRule>,
        packageName: String,
        channelId: String?,
        title: String,
        text: String,
    ): RuleMatchResult {
        // Filter rules applicable to this notification (global or package-matching, and enabled)
        val applicable = rules.filter { it.enabled && (it.packageName == null || it.packageName == packageName) }

        // Tier 2: Whitelist (KEEP) rules first
        for (rule in applicable.filter { it.verdict == Verdict.KEEP }) {
            if (isMatch(rule, channelId, title, text)) {
                val label = rule.description ?: "rule '${rule.pattern}'"
                return RuleMatchResult(
                    matched = true,
                    verdict = Verdict.KEEP,
                    rule = rule,
                    reason = "Kept by $label",
                )
            }
        }

        // Tier 3: Blacklist (HIDE) rules second
        for (rule in applicable.filter { it.verdict == Verdict.HIDE }) {
            if (isMatch(rule, channelId, title, text)) {
                val label = rule.description ?: "rule '${rule.pattern}'"
                return RuleMatchResult(
                    matched = true,
                    verdict = Verdict.HIDE,
                    rule = rule,
                    reason = "Hidden by $label",
                )
            }
        }

        return RuleMatchResult.NO_MATCH
    }

    fun isMatch(
        rule: NotificationRule,
        channelId: String?,
        title: String,
        text: String,
    ): Boolean {
        val targetText = when (rule.matchTarget) {
            MatchTarget.ANY -> "$title $text"
            MatchTarget.TITLE -> title
            MatchTarget.TEXT -> text
            MatchTarget.CHANNEL -> channelId.orEmpty()
        }

        if (targetText.isBlank() && rule.matchTarget != MatchTarget.CHANNEL) return false

        return if (rule.isRegex) {
            runCatching {
                Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(targetText)
            }.getOrDefault(false)
        } else {
            targetText.contains(rule.pattern, ignoreCase = true)
        }
    }
}
