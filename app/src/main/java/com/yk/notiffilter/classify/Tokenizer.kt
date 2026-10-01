package com.yk.notiffilter.classify

import java.util.Locale

/**
 * Turns a notification into the feature set the classifier scores.
 *
 * Deliberately *not* [com.yk.notiffilter.fingerprint.Fingerprinter.normalize]. That
 * function strips digits, punctuation and emoji, which is right for template identity
 * and catastrophic here: "%", "₹", "🎉", "!" and SHOUTING are among the strongest
 * marketing signals in the string, and a marketer can reword the sentence around them
 * but rarely drops them. Identity and classification want opposite things from the
 * same text, so they get their own normalisation.
 *
 * Features are a *set*, not a bag. With a few dozen labelled examples, counting
 * repetitions lets one emoji-spattered notification dominate the model; presence or
 * absence is the more stable signal on text this short.
 */
object Tokenizer {

    /** Prefixes exist so a word in a title can carry different evidence than the same word in the body. */
    private const val WORD = "w:"
    private const val TITLE_WORD = "ti:"
    private const val CHANNEL = "ch:"

    private val WORD_SPLIT = Regex("""[^\p{L}\p{N}']+""")
    private val URL = Regex("""https?://|www\.""")
    private val ALL_CAPS = Regex("""\b\p{Lu}{3,}\b""")
    private val DIGIT_RUN = Regex("""\d+""")
    private val CURRENCY = Regex("""[₹$€£¥]|\brs\.?\b""", RegexOption.IGNORE_CASE)

    /**
     * Stop words are deliberately *not* removed. "your", "now", "today" and "you" are
     * among the most reliable promotional markers in notification copy — the register
     * is second-person and imperative — so discarding them would throw away signal.
     */
    fun features(channelId: String?, title: String, text: String): Set<String> {
        val features = LinkedHashSet<String>()

        channelId?.takeIf { it.isNotBlank() }?.let { features += CHANNEL + it }

        words(title).forEach {
            features += WORD + it
            features += TITLE_WORD + it
        }
        words(text).forEach { features += WORD + it }

        features += shapes(title, text)
        return features
    }

    /**
     * Shape features survive rewording entirely: a discount is still a discount when
     * "FLAT 50% OFF" becomes "Grab 40% off today". These are what stop the model from
     * having to relearn every new campaign from scratch.
     */
    fun shapes(title: String, text: String): Set<String> {
        val raw = "$title $text"
        val shapes = LinkedHashSet<String>()

        if (raw.contains('%')) shapes += "«pct»"
        if (CURRENCY.containsMatchIn(raw)) shapes += "«currency»"
        if (raw.contains('!')) shapes += "«excl»"
        if (URL.containsMatchIn(raw)) shapes += "«url»"
        if (ALL_CAPS.containsMatchIn(raw)) shapes += "«shout»"
        if (hasEmoji(raw)) shapes += "«emoji»"

        // A long digit run is an order id, a reference or a code — the vocabulary of
        // transactional messages. A short one is a price, a count or a discount.
        DIGIT_RUN.findAll(raw).forEach {
            shapes += if (it.value.length >= 4) "«longnum»" else "«num»"
        }
        return shapes
    }

    private fun words(raw: String): List<String> =
        raw.lowercase(Locale.ROOT)
            .split(WORD_SPLIT)
            .filter { it.length > 1 && !it.all(Char::isDigit) }

    /**
     * Range check rather than a library call: this has to run identically on the JVM
     * under unit test and on the device, and the emoji blocks are stable enough that
     * a handful of ranges beats a dependency.
     */
    private fun hasEmoji(raw: String): Boolean {
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            val isEmoji = cp in 0x1F300..0x1FAFF ||   // pictographs, faces, symbols
                cp in 0x1F000..0x1F2FF ||             // tiles, enclosed characters
                cp in 0x2600..0x27BF ||               // misc symbols and dingbats
                cp in 0x2B00..0x2BFF ||               // arrows and stars
                cp == 0xFE0F                          // variation selector-16
            if (isEmoji) return true
            i += Character.charCount(cp)
        }
        return false
    }
}
