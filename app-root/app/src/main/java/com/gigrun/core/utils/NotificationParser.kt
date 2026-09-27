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
     * Handles formats: ₹52, ₹1,250 (Indian grouping), ₹52.50, Rs. 340, INR 500.
     * \b guards stop "Rs" matching inside words ("hours 50", "bars 100").
     */
    private val RUPEE_PATTERNS = listOf(
        Regex("""₹\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""\bRs\.?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""\bINR\b\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // Bare keyword numbers ("Payout 320") — but never counts of stars,
        // points, trips, orders or ratings ("earned 5 stars" is not ₹5).
        Regex("""(?:earned|payout|fare|earning|payment)[:\s]+₹?\s*([\d,]+(?:\.\d{1,2})?)(?!\s*(?:stars?|points?|trips?|orders?|ratings?|km))""", RegexOption.IGNORE_CASE)
    )

    /** Keywords indicating an earnings-related notification */
    private val EARNINGS_KEYWORDS = listOf(
        "earned", "payout", "fare", "earning", "payment", "completed",
        "delivered", "trip completed", "order completed", "credited"
    )

    /**
     * Rider-side copy: the user PAID, not earned. A passenger receipt from the
     * rider app ("thanks for riding") must never become driver income.
     */
    private val RIDER_KEYWORDS = listOf(
        "thanks for riding", "thank you for riding", "your receipt",
        "you were charged", "paid by you", "debited from", "ride receipt"
    )

    /** Keywords indicating a new order notification */
    private val ORDER_KEYWORDS = listOf(
        "new order", "order assigned", "pickup", "go to", "accept",
        "new trip", "ride request", "delivery request"
    )

    private val SURGE_KEYWORDS = listOf("surge", "peak", "high demand", "incentive", "bonus", "extra")
    private val TIP_KEYWORDS = listOf("tip", "tips", "gratuity")

    data class ParseResult(
        val amount: Double?,
        val rawText: String,
        val platform: Platform,
        val isEarnings: Boolean,
        val isNewOrder: Boolean,
        val surgeAmount: Double? = null,
        val bonusAmount: Double? = null,
        val tipAmount: Double? = null,
        val baseFare: Double? = null,
        val surgeReason: String? = null
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
        // ROOT locale: Turkish-locale lowercase breaks keyword matching.
        val lowerText = fullText.lowercase(java.util.Locale.ROOT)

        val isRiderReceipt = RIDER_KEYWORDS.any { lowerText.contains(it) }
        val isEarnings = !isRiderReceipt && EARNINGS_KEYWORDS.any { lowerText.contains(it) }
        val isNewOrder = ORDER_KEYWORDS.any { lowerText.contains(it) }

        // Try to extract the highest rupee amount from the text.
        // Indian grouping ("1,250") is stripped before parsing — toDoubleOrNull
        // rejects commas, which used to zero out comma-formatted fares.
        // Keyword-anchored amounts ("earned ₹50" inside "saved ₹200 ... earned
        // ₹50") win over the bare max, so promo copy never outranks the fare.
        val keywordAnchored = mutableListOf<Double>()
        val anchored = mutableListOf<Double>()
        val bare = mutableListOf<Double>()
        val keywordPattern = Regex("""(?:earned|payout|pay|salary|credited|bonus|incentive)\b[^₹\d]{0,20}₹\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        keywordPattern.findAll(fullText).forEach { match ->
            match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.let {
                keywordAnchored.add(it)
            }
        }
        val anchoredPatterns = RUPEE_PATTERNS.take(3)
        for (pattern in anchoredPatterns) {
            pattern.findAll(fullText).forEach { match ->
                match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.let {
                    anchored.add(it)
                }
            }
        }
        RUPEE_PATTERNS.drop(3).forEach { pattern ->
            pattern.findAll(fullText).forEach { match ->
                match.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()?.let {
                    bare.add(it)
                }
            }
        }
        val pool = when {
            keywordAnchored.isNotEmpty() -> keywordAnchored
            anchored.isNotEmpty() -> anchored
            else -> bare
        }

        // Pick the highest amount (most likely the total fare, not a tip or surge component)
        // R&D fix: never return an amount for non-earnings notifications (prevents false positives).
        val amount = if (pool.isNotEmpty() && isEarnings) pool.max() else null

        // Surge / bonus / tip extraction
        var surgeAmount: Double? = null
        var bonusAmount: Double? = null
        var tipAmount: Double? = null
        var surgeReason: String? = null

        // Look for surge patterns: "surge ₹15", "peak bonus ₹20", "incentive ₹30"
        // Supports ₹, Rs, INR variants (Indian gig apps mix formats).
        val money = """(?:₹|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)"""
        fun maxMoney(pattern: Regex): Double? =
            pattern.findAll(fullText).mapNotNull { it.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() }.maxOrNull()
        val surgePattern = Regex("""(?:surge|peak|incentive|bonus)\s*[:\-]?\s*$money""", RegexOption.IGNORE_CASE)
        surgeAmount = maxMoney(surgePattern)
        val bonusPattern = Regex("""(?:bonus|extra)\s*$money""", RegexOption.IGNORE_CASE)
        bonusAmount = maxMoney(bonusPattern)
        val tipPattern = Regex("""(?:tip|tips|gratuity)\s*[:\-]?\s*$money""", RegexOption.IGNORE_CASE)
        tipAmount = maxMoney(tipPattern)

        // Detect surge reason
        when {
            lowerText.contains("peak") -> surgeReason = "peak_hour"
            lowerText.contains("rain") -> surgeReason = "rain"
            lowerText.contains("high demand") -> surgeReason = "high_demand"
            lowerText.contains("surge") -> surgeReason = "surge"
            lowerText.contains("festival") -> surgeReason = "festival"
        }

        // Base fare = total - surge - bonus - tip if breakdown exists
        val baseFare = if (surgeAmount != null || bonusAmount != null || tipAmount != null) {
            amount?.let { it - (surgeAmount ?: 0.0) - (bonusAmount ?: 0.0) - (tipAmount ?: 0.0) }?.coerceAtLeast(0.0)
        } else null

        return ParseResult(
            amount = amount,
            rawText = fullText,
            platform = platform,
            isEarnings = isEarnings,
            isNewOrder = isNewOrder,
            surgeAmount = surgeAmount,
            bonusAmount = bonusAmount,
            tipAmount = tipAmount,
            baseFare = baseFare,
            surgeReason = surgeReason
        )
    }
}
