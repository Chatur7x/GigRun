package com.gigrun.core.utils

import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {

    @Test
    fun parse_blinkitEarningNotification_extractsCorrectAmountAndPlatform() {
        val packageName = "com.blinkit.delivery"
        val title = "Order Completed"
        val text = "You have earned ₹75.50 for this delivery."

        val result = NotificationParser.parse(packageName, title, text)

        assertEquals(NotificationParser.Platform.BLINKIT, result.platform)
        assertTrue(result.isEarnings)
        assertFalse(result.isNewOrder)
        assertNotNull(result.amount)
        assertEquals(75.50, result.amount!!, 0.001)
    }

    @Test
    fun parse_zeptoNewOrderNotification_detectsCorrectPlatformAndFlags() {
        val packageName = "com.zepto.delivery"
        val title = "New Order Assigned"
        val text = "Pickup from Hub A. Go to customer location."

        val result = NotificationParser.parse(packageName, title, text)

        assertEquals(NotificationParser.Platform.ZEPTO, result.platform)
        assertFalse(result.isEarnings)
        assertTrue(result.isNewOrder)
        assertNull(result.amount)
    }

    @Test
    fun parse_rapidoCaptainEarningWithDifferentCurrencySymbols() {
        val packageName = "com.rapido.captain"
        val title = "Ride Completed"
        
        // Test different formats
        val textRs = "Payout of Rs. 120 credited to wallet"
        val resultRs = NotificationParser.parse(packageName, title, textRs)
        assertEquals(120.0, resultRs.amount ?: 0.0, 0.001)
        assertTrue(resultRs.isEarnings)

        val textInr = "Earning: INR 350 for ride #1234"
        val resultInr = NotificationParser.parse(packageName, title, textInr)
        assertEquals(350.0, resultInr.amount ?: 0.0, 0.001)
    }

    @Test
    fun parse_unknownPackageName_tagsAsUnknown() {
        val packageName = "com.random.app"
        val title = "Random Title"
        val text = "Random message text ₹50"

        val result = NotificationParser.parse(packageName, title, text)

        assertEquals(NotificationParser.Platform.UNKNOWN, result.platform)
    }
}
