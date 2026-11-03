package com.yk.notiffilter

import com.yk.notiffilter.classify.Tokenizer
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource
import com.yk.notiffilter.service.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FilterEngineTest {

    @Test
    fun `findFuzzyMatch catches campaign variations of user-pinned templates`() {
        val userPinned = listOf(
            TemplateVerdict(
                fingerprint = "fp-1",
                packageName = "in.swiggy.android",
                verdict = Verdict.HIDE,
                reason = "You decided",
                source = VerdictSource.USER,
                needsVerdict = false,
                score = 10.0,
                channelId = "marketing_notification_id",
                sampleTitle = "Meetings are hard enough. 😭",
                sampleText = "Make lunch easy with 70% OFF!",
            ),
        )

        // Slight variation in title/text: "Mondays" instead of "Meetings", same core promotional content
        val variantFeatures = Tokenizer.features(
            channelId = "marketing_notification_id",
            title = "Mondays are hard enough. 😭",
            text = "Make lunch easy with 70% OFF!",
        )

        val match = FilterEngine.findFuzzyMatch(variantFeatures, userPinned, threshold = 0.60)

        assertNotNull("Expected fuzzy match for marketing variant", match)
        assertEquals(Verdict.HIDE, match?.verdict)
    }

    @Test
    fun `findFuzzyMatch does not match unrelated transactional notification`() {
        val userPinned = listOf(
            TemplateVerdict(
                fingerprint = "fp-1",
                packageName = "in.swiggy.android",
                verdict = Verdict.HIDE,
                reason = "You decided",
                source = VerdictSource.USER,
                needsVerdict = false,
                score = 10.0,
                channelId = "marketing_notification_id",
                sampleTitle = "Meetings are hard enough. 😭",
                sampleText = "Make lunch easy with 70% OFF!",
            ),
        )

        val deliveryFeatures = Tokenizer.features(
            channelId = "order_status",
            title = "Order Out for Delivery",
            text = "Your rider is on the way with your food",
        )

        val match = FilterEngine.findFuzzyMatch(deliveryFeatures, userPinned, threshold = 0.60)

        assertNull("Transactional message must not fuzzy match promotional HIDE", match)
    }
}
