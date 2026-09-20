package com.yk.notiffilter.fingerprint

import java.security.MessageDigest
import java.util.Locale

/**
 * Collapses a notification into a stable template identity.
 *
 * Deliberately conservative: any real wording change produces a new fingerprint and
 * costs one free classification call, whereas two different messages colliding onto
 * one fingerprint would silently apply the wrong verdict and be very hard to notice.
 * Those errors are wildly asymmetric, so this biases hard towards over-splitting.
 *
 * Note what is *not* stripped: proper nouns. There is no general way to recognise a
 * restaurant or courier name, so "Arriving from Meghana Foods" and "Arriving from
 * Empire" are separate templates. That is the over-split bias working as intended.
 */
object Fingerprinter {

    private val URL = Regex("""https?://\S+|www\.\S+""")
    private val DIGITS = Regex("""\d+""")

    /**
     * Digit runs become a sentinel first, so the literal "#" in "#12345" is stripped as
     * ordinary punctuation rather than surviving alongside the placeholder and turning
     * one variable into two characters.
     */
    private const val DIGIT_SENTINEL = "\u0000"

    /** Anything that is not a letter, a space, or the sentinel. Drops emoji too. */
    private val NOISE = Regex("""[^\p{L} \u0000]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Human-readable template form. Exposed separately from [fingerprint] so the
     * normalisation can be inspected and unit-tested directly rather than only
     * through an opaque hash.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.lowercase(Locale.ROOT)
            .replace(URL, " ")
            .replace(DIGITS, DIGIT_SENTINEL)
            .replace(NOISE, " ")
            .replace(DIGIT_SENTINEL, "#")
            .replace(WHITESPACE, " ")
            .trim()
    }

    /**
     * [channelId] is included because many apps already separate promotional and
     * transactional notifications into different channels, which makes it a strong
     * signal that costs nothing to use.
     */
    fun fingerprint(
        packageName: String,
        channelId: String?,
        title: String?,
        text: String?,
    ): String {
        val template = buildString {
            append(packageName).append('|')
            append(channelId.orEmpty()).append('|')
            append(normalize(title)).append('|')
            append(normalize(text))
        }
        return sha256(template)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
