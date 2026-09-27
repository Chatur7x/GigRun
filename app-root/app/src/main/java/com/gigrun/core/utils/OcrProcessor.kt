package com.gigrun.core.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class OcrResult(
    val platform: String = "untagged",
    val earnings: Double = 0.0,
    val distanceKm: Double = 0.0,
    val trips: Int = 0
)

object OcrProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Release the shared ML Kit client (native resources) — call on app teardown. */
    fun close() {
        try { recognizer.close() } catch (_: Exception) {}
    }

    suspend fun processScreenshot(bitmap: Bitmap, rotationDegrees: Int = 0): OcrResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            val result = recognizer.process(image).await()
            parseText(result.text)
        } catch (e: Exception) {
            e.printStackTrace()
            OcrResult()
        }
    }

    fun parseText(text: String): OcrResult {
        if (text.isBlank()) return OcrResult()

        val lines = text.lines().map { it.trim() }
        var platform = "untagged"
        var earnings = 0.0
        var distanceKm = 0.0
        var trips = 0

        // Parse Platform
        val lowerText = text.lowercase()
        when {
            lowerText.contains("uber") -> platform = "Uber"
            lowerText.contains("rapido") -> platform = "Rapido"
            lowerText.contains("blinkit") -> platform = "Blinkit"
            lowerText.contains("zepto") -> platform = "Zepto"
            lowerText.contains("swiggy") -> platform = "Swiggy"
            lowerText.contains("zomato") -> platform = "Zomato"
            lowerText.contains("bigbasket") -> platform = "BigBasket"
        }

        // Parse Earnings/Payout (e.g. ₹ 450, ₹1,250, Payout: 320, Rs 150)
        // R&D fix: old char-class [₹|Rs\.?|INR] matched literal '|' — use alternation.
        // \b guards stop Rs/INR matching inside words; commas stripped (Indian grouping).
        val earningsRegexes = listOf(
            Regex("""(?:₹|\bRs\.?|\bINR\b)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?i)(?:payout|earned|earning|total)\s*[:|=]?\s*₹?\s*([\d,]+(?:\.\d{1,2})?)"""),
            Regex("""(?i)payout\s+([\d,]+(?:\.\d{1,2})?)""")
        )
        for (regex in earningsRegexes) {
            val match = regex.find(text)
            if (match != null) {
                earnings = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                if (earnings > 0.0) break
            }
        }

        // Parse Distance (e.g. 12.5 km, 8kms, 4.2 kilometers).
        // \b + negative lookahead: "100kmph" is speed, not distance.
        val distanceRegexes = listOf(
            Regex("""(\d+(?:\.\d+)?)\s*(?:km|kilometers)(?![a-z])""", RegexOption.IGNORE_CASE),
            Regex("""(?i)(?:\bdist\b|distance)\s*[:|=]?\s*(\d+(?:\.\d+)?)\s*(?:km|kms)?""")
        )
        for (regex in distanceRegexes) {
            val match = regex.find(text)
            if (match != null) {
                distanceKm = match.groupValues[1].toDoubleOrNull() ?: 0.0
                if (distanceKm > 0.0) break
            }
        }

        // Parse Trips/Orders (e.g. 12 orders, 8 trips, Trips: 10)
        val tripsRegexes = listOf(
            Regex("""(\d+)\s*(?:trips|orders|deliveries|gigs)""", RegexOption.IGNORE_CASE),
            Regex("""(?i)(?:trips|orders|deliveries)\s*[:|=]?\s*(\d+)""")
        )
        for (regex in tripsRegexes) {
            val match = regex.find(text)
            if (match != null) {
                trips = match.groupValues[1].toIntOrNull() ?: 0
                if (trips > 0) break
            }
        }

        return OcrResult(
            platform = platform,
            earnings = earnings,
            distanceKm = distanceKm,
            trips = trips
        )
    }
}
