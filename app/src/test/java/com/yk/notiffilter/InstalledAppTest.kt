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
}
