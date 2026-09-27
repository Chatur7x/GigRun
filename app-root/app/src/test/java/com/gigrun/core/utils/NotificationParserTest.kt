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

    @Test
    fun parse_surgeSplit_computesBaseFare() {
        val result = NotificationParser.parse(
            "com.rapido.captain", "Ride Completed",
            "You earned ₹120. Surge ₹15. Tip ₹10. Peak hour bonus."
        )
        assertEquals(120.0, result.amount ?: 0.0, 0.001)
        assertEquals(15.0, result.surgeAmount ?: 0.0, 0.001)
        assertEquals(10.0, result.tipAmount ?: 0.0, 0.001)
        assertEquals(95.0, result.baseFare ?: 0.0, 0.001)
        assertEquals("peak_hour", result.surgeReason)
    }

    @Test
    fun parse_commaGroupedFare_parses() {
        val result = NotificationParser.parse(
            "com.blinkit.delivery", "Order Completed",
            "You have earned ₹1,250 for this delivery."
        )
        assertEquals(1250.0, result.amount ?: 0.0, 0.001)
    }

    @Test
    fun parse_nonEarningsWithAmount_returnsNullAmount() {
        val result = NotificationParser.parse(
            "com.rapido.captain", "New ride",
            "Pickup in 5 min, trip shows ₹120 estimate. Go to customer."
        )
        // "go to" is an order keyword, not earnings — amount must stay null.
        assertFalse(result.isEarnings)
        assertNull(result.amount)
    }

    @Test
    fun parse_riderReceipt_neverEarnings() {
        val result = NotificationParser.parse(
            "com.rapido.passenger", "Thanks for riding!",
            "Payment of ₹150 completed. Your receipt is ready."
        )
        assertFalse(result.isEarnings)
        assertNull(result.amount)
    }

    @Test
    fun parse_promoCopy_prefersKeywordAnchoredFare() {
        // "saved ₹200" is promo; "earned ₹50" is the fare — max must not win.
        val result = NotificationParser.parse(
            "com.blinkit.delivery", "Order delivered!",
            "You saved ₹200 on this order. Cashback earned ₹50"
        )
        assertTrue(result.isEarnings)
        assertEquals(50.0, result.amount ?: 0.0, 0.001)
    }

    @Test
    fun parse_starRating_notFare() {
        val result = NotificationParser.parse(
            "com.ubercab.driver", "Weekly rating",
            "You earned 5 stars this week! Rating bonus inside"
        )
        assertNull(result.amount)
    }
}
