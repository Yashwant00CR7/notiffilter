package com.yk.notiffilter

import com.yk.notiffilter.ui.screens.InstalledApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppTest {

    @Test
    fun `matches by label`() {
        val app = InstalledApp(
            packageName = "in.swiggy.android",
            label = "Swiggy",
        )
        assertTrue(app.matches("swiggy"))
        assertTrue(app.matches("Swiggy"))
        assertTrue(app.matches("SWIG"))
        assertFalse(app.matches("Zomato"))
    }

    @Test
    fun `matches by packageName`() {
        val app = InstalledApp(
            packageName = "com.whatsapp",
            label = "WhatsApp Messenger",
        )
        assertTrue(app.matches("whatsapp"))
        assertTrue(app.matches("com.whatsapp"))
    }

    @Test
    fun `matches by alias such as Amazon Pay on Amazon app`() {
        val amazonApp = InstalledApp(
            packageName = "in.amazon.mShop.android.shopping",
            label = "Amazon",
            aliases = listOf("Amazon Pay", "Amazon Shopping", "Apay", "Amazon UPI"),
        )
        assertTrue(amazonApp.matches("Amazon"))
        assertTrue(amazonApp.matches("Amazon Pay"))
        assertTrue(amazonApp.matches("pay"))
        assertTrue(amazonApp.matches("Pay"))
        assertTrue(amazonApp.matches("Apay"))
        assertTrue(amazonApp.matches("UPI"))
        assertFalse(amazonApp.matches("Flipkart"))
    }

    @Test
    fun `matches dedicated Amazon Pay entry`() {
        val amazonPay = InstalledApp(
            packageName = "in.amazon.mShop.android.shopping",
            label = "Amazon Pay",
            aliases = listOf("Amazon", "Pay", "UPI", "Apay", "Amazon Shopping", "Wallet"),
        )
        assertTrue(amazonPay.matches("Amazon"))
        assertTrue(amazonPay.matches("Amazon Pay"))
        assertTrue(amazonPay.matches("pay"))
        assertTrue(amazonPay.matches("UPI"))
        assertTrue(amazonPay.matches("Apay"))
        assertFalse(amazonPay.matches("Swiggy"))
    }

    @Test
    fun `matches SMS and Messages alias for SMS apps`() {
        val messagesApp = InstalledApp(
            packageName = "com.google.android.apps.messaging",
            label = "Messages",
            aliases = listOf("SMS", "Messages", "Text"),
        )
        assertTrue(messagesApp.matches("SMS"))
        assertTrue(messagesApp.matches("sms"))
        assertTrue(messagesApp.matches("text"))
        assertTrue(messagesApp.matches("Messages"))
    }
}
