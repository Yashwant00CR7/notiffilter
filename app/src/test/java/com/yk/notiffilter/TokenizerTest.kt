package com.yk.notiffilter

import com.yk.notiffilter.classify.Tokenizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {

    @Test
    fun `keeps the signals the fingerprinter deliberately strips`() {
        // The whole reason this is not Fingerprinter.normalize: that function throws
        // away every one of these.
        val features = Tokenizer.features("promo", "🔥 FLAT 60% OFF", "Order now and save ₹250!")

        assertTrue("«pct»" in features)
        assertTrue("«currency»" in features)
        assertTrue("«emoji»" in features)
        assertTrue("«shout»" in features)
        assertTrue("«excl»" in features)
    }

    @Test
    fun `a transactional message carries none of the promotional shapes`() {
        val features = Tokenizer.features("orders", "Order Delivered", "Enjoy your meal")

        assertFalse("«pct»" in features)
        assertFalse("«currency»" in features)
        assertFalse("«emoji»" in features)
        assertFalse("«excl»" in features)
    }

    @Test
    fun `channel is a feature in its own right`() {
        assertTrue("ch:promo" in Tokenizer.features("promo", "Update", "Something happened"))
        assertTrue("ch:orders" in Tokenizer.features("orders", "Update", "Something happened"))
    }

    @Test
    fun `title words carry extra evidence over body words`() {
        val features = Tokenizer.features(null, "Refund", "your refund is on the way")

        assertTrue("ti:refund" in features)
        assertTrue("w:refund" in features)
        // "way" only appears in the body, so it gets no title-weighted counterpart.
        assertTrue("w:way" in features)
        assertFalse("ti:way" in features)
    }

    @Test
    fun `long digit runs are distinguished from short ones`() {
        // An order id reads as transactional; a discount number reads as promotional.
        assertTrue("«longnum»" in Tokenizer.shapes("Order #40318827", ""))
        assertTrue("«num»" in Tokenizer.shapes("50% off", ""))
        assertFalse("«longnum»" in Tokenizer.shapes("50% off", ""))
    }

    @Test
    fun `features are a set so repetition cannot dominate`() {
        val spammy = Tokenizer.features(null, "SALE SALE SALE", "sale sale sale")
        assertTrue(spammy.count { it == "w:sale" } == 1)
    }

    @Test
    fun `stop words are kept because the promotional register lives in them`() {
        val features = Tokenizer.features(null, "Your favourite is back", "Order now")
        assertTrue("w:your" in features)
        assertTrue("w:now" in features)
    }

    @Test
    fun `urls are recognised as a shape`() {
        assertTrue("«url»" in Tokenizer.shapes("", "Grab it at https://swiggy.app/x9"))
    }

    @Test
    fun `extracts word bigrams from title and text`() {
        val features = Tokenizer.features(null, "Order Now", "Grab your meal")
        assertTrue("bi:order_now" in features)
        assertTrue("tibi:order_now" in features)
        assertTrue("bi:grab_your" in features)
        assertTrue("bi:your_meal" in features)
    }
}
