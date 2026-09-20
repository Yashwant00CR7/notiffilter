package com.yk.notiffilter

import com.yk.notiffilter.fingerprint.Fingerprinter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprinterTest {

    @Test
    fun `varying amounts and emoji collapse to one template`() {
        val a = Fingerprinter.fingerprint(
            "in.swiggy.android", "promo", "🔥 FLAT 60% OFF", "Order now and save ₹250!",
        )
        val b = Fingerprinter.fingerprint(
            "in.swiggy.android", "promo", "🎉 FLAT 40% OFF", "Order now and save ₹120!",
        )
        assertEquals(a, b)
    }

    @Test
    fun `different messages never collide`() {
        val delivered = Fingerprinter.fingerprint(
            "in.swiggy.android", "orders", "Order Delivered", "Enjoy your meal!",
        )
        val delayed = Fingerprinter.fingerprint(
            "in.swiggy.android", "orders", "Order Delayed", "Enjoy your meal!",
        )
        assertNotEquals(delivered, delayed)
    }

    @Test
    fun `channel separates otherwise identical text`() {
        val promo = Fingerprinter.fingerprint("com.x", "promo", "Update", "Something happened")
        val orders = Fingerprinter.fingerprint("com.x", "orders", "Update", "Something happened")
        assertNotEquals(promo, orders)
    }

    @Test
    fun `same text from different apps is a different template`() {
        val a = Fingerprinter.fingerprint("com.a", "c", "Order Delivered", "Enjoy")
        val b = Fingerprinter.fingerprint("com.b", "c", "Order Delivered", "Enjoy")
        assertNotEquals(a, b)
    }

    @Test
    fun `normalisation strips digits urls and punctuation but keeps words`() {
        assertEquals(
            "your order # is on the way",
            Fingerprinter.normalize("Your order #12345 is on the way! https://swiggy.app/x9"),
        )
    }

    @Test
    fun `proper nouns deliberately split templates`() {
        // No general way to recognise a restaurant name, so these stay separate.
        // That is the over-split bias working as intended: an extra free call beats
        // a silent mis-merge.
        val a = Fingerprinter.fingerprint("com.x", "c", "Arriving", "From Meghana Foods")
        val b = Fingerprinter.fingerprint("com.x", "c", "Arriving", "From Empire Restaurant")
        assertNotEquals(a, b)
    }

    @Test
    fun `a literal hash does not survive alongside the digit placeholder`() {
        // Regression: "#" was both an allowed literal and the digit placeholder, so
        // "#12345" normalised to "##" and split templates that should have merged.
        assertEquals(Fingerprinter.normalize("order 12345"), Fingerprinter.normalize("order #12345"))
        assertEquals("order #", Fingerprinter.normalize("order #12345"))
    }

    @Test
    fun `blank and null inputs normalise to empty`() {
        assertEquals("", Fingerprinter.normalize(null))
        assertEquals("", Fingerprinter.normalize("   "))
    }
}
