package com.gigrun.core.utils

/**
 * Parses notification text from delivery platform apps
 * to extract earnings amounts and platform identification.
 */
object NotificationParser {

    /** Known delivery platform package mappings */
    enum class Platform(val displayName: String, vararg val packages: String) {
        BLINKIT("Blinkit", "com.grofers.delivery", "com.blinkit.delivery"),
        ZEPTO("Zepto", "com.zepto.delivery", "com.shadowfax.delivery", "com.zeptonow.delivery"),
        RAPIDO("Rapido", "com.rapido.passenger", "com.rapido.driver", "com.rapido.captain"),
        UBER("Uber", "com.ubercab.driver"),
        UNKNOWN("Untagged");

        companion object {
            fun fromPackage(packageName: String): Platform {
                return entries.find { platform ->
                    platform.packages.any { it == packageName }
                } ?: UNKNOWN
            }
        }
    }

    /**
     * Regex patterns to extract rupee amounts from notification text.
     * Handles formats: ₹52, ₹52.50, Rs. 340, Rs 340.00, INR 500
     */
    private val RUPEE_PATTERNS = listOf(
        Regex("""₹\s*(\d+(?:\.\d{1,2})?)"""),
        Regex("""Rs\.?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""INR\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:earned|payout|fare|earning|payment)[:\s]+₹?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    /** Keywords indicating an earnings-related notification */
    private val EARNINGS_KEYWORDS = listOf(
        "earned", "payout", "fare", "earning", "payment", "completed",
        "delivered", "trip completed", "order completed", "credited"
    )

    /** Keywords indicating a new order notification */
    private val ORDER_KEYWORDS = listOf(
        "new order", "order assigned", "pickup", "go to", "accept",
        "new trip", "ride request", "delivery request"
    )

    data class ParseResult(
        val amount: Double?,
        val rawText: String,
        val platform: Platform,
        val isEarnings: Boolean,
        val isNewOrder: Boolean
    )

    /**
     * Parses a notification from a delivery app.
     * @param packageName The source app's package name
     * @param title The notification title (can be null)
     * @param text The notification body text (can be null)
     * @return ParseResult with extracted data
     */
    fun parse(packageName: String, title: String?, text: String?): ParseResult {
        val platform = Platform.fromPackage(packageName)
        val fullText = listOfNotNull(title, text).joinToString(" ")
        val lowerText = fullText.lowercase()

        val isEarnings = EARNINGS_KEYWORDS.any { lowerText.contains(it) }
        val isNewOrder = ORDER_KEYWORDS.any { lowerText.contains(it) }

        // Try to extract the highest rupee amount from the text
        val amounts = mutableListOf<Double>()
        for (pattern in RUPEE_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                match.groupValues.getOrNull(1)?.toDoubleOrNull()?.let {
                    amounts.add(it)
                }
            }
        }

        // Pick the highest amount (most likely the total fare, not a tip or surge component)
        val amount = if (isEarnings && amounts.isNotEmpty()) amounts.max() else amounts.maxOrNull()

        return ParseResult(
            amount = amount,
            rawText = fullText,
            platform = platform,
            isEarnings = isEarnings,
            isNewOrder = isNewOrder
        )
    }
}
