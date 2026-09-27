package com.gigrun.core.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates and exports shift report PDFs using Android's native PdfDocument API.
 * No third-party libraries required.
 */
object PdfExporter {

    data class ShiftReportData(
        val dateRange: String,
        val totalTrips: Int,
        val totalDistanceKm: Double,
        val totalShiftTimeMinutes: Long,
        val totalRidingTimeMinutes: Long,
        val totalWaitTimeMinutes: Long,
        val grossEarnings: Double,
        val fuelCost: Double,
        val netEarnings: Double,
        val grossPerHour: Double,
        val netPerHour: Double,
        val platformBreakdown: List<PlatformSummary>
    )

    data class PlatformSummary(
        val name: String,
        val trips: Int,
        val earnings: Double,
        val netPerHour: Double,
        val avgWaitMinutes: Double,
        val distanceKm: Double
    )

    /**
     * Generates a PDF report file and returns the file path.
     */
    fun generateReport(context: Context, data: ShiftReportData): File {
        val document = PdfDocument()
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1A2E")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#16213E")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 12f
            isAntiAlias = true
        }

        val valuePaint = Paint().apply {
            color = Color.parseColor("#0F3460")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
        }

        var y = 50f
        val leftMargin = 40f
        val valueX = 300f

        // Title
        canvas.drawText("GigRun — Shift Report", leftMargin, y, titlePaint)
        y += 25f
        canvas.drawText(data.dateRange, leftMargin, y, bodyPaint)
        y += 30f
        canvas.drawLine(leftMargin, y, 555f, y, linePaint)
        y += 25f

        // Summary Section
        canvas.drawText("SHIFT SUMMARY", leftMargin, y, headerPaint)
        y += 25f

        fun drawRow(label: String, value: String) {
            canvas.drawText(label, leftMargin + 10f, y, bodyPaint)
            canvas.drawText(value, valueX, y, valuePaint)
            y += 20f
        }

        drawRow("Total Trips", "${data.totalTrips}")
        drawRow("Total Distance", String.format(java.util.Locale.US, "%.1f km", data.totalDistanceKm))
        drawRow("Shift Time", "${data.totalShiftTimeMinutes / 60}h ${data.totalShiftTimeMinutes % 60}m")
        drawRow("Riding Time", "${data.totalRidingTimeMinutes / 60}h ${data.totalRidingTimeMinutes % 60}m")
        drawRow("Unpaid Wait Time", "${data.totalWaitTimeMinutes / 60}h ${data.totalWaitTimeMinutes % 60}m")
        y += 10f
        canvas.drawLine(leftMargin, y, 555f, y, linePaint)
        y += 25f

        // Earnings Section
        canvas.drawText("EARNINGS", leftMargin, y, headerPaint)
        y += 25f
        drawRow("Gross Earnings", String.format(java.util.Locale.US, "₹ %.0f", data.grossEarnings))
        drawRow("Fuel Cost", String.format(java.util.Locale.US, "₹ %.0f", data.fuelCost))
        drawRow("Net Earnings", String.format(java.util.Locale.US, "₹ %.0f", data.netEarnings))
        drawRow("Gross ₹/hour", String.format(java.util.Locale.US, "₹ %.0f/hr", data.grossPerHour))
        drawRow("Net ₹/hour", String.format(java.util.Locale.US, "₹ %.0f/hr", data.netPerHour))
        y += 10f
        canvas.drawLine(leftMargin, y, 555f, y, linePaint)
        y += 25f

        // Platform Breakdown
        if (data.platformBreakdown.isNotEmpty()) {
            canvas.drawText("PLATFORM BREAKDOWN", leftMargin, y, headerPaint)
            y += 25f

            // Table header
            val colX = listOf(leftMargin, 130f, 210f, 300f, 390f, 480f)
            val tableHeaderPaint = Paint(bodyPaint).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Platform", colX[0], y, tableHeaderPaint)
            canvas.drawText("Trips", colX[1], y, tableHeaderPaint)
            canvas.drawText("Earned", colX[2], y, tableHeaderPaint)
            canvas.drawText("₹/hr", colX[3], y, tableHeaderPaint)
            canvas.drawText("Avg Wait", colX[4], y, tableHeaderPaint)
            canvas.drawText("Distance", colX[5], y, tableHeaderPaint)
            y += 18f
            canvas.drawLine(leftMargin, y, 555f, y, linePaint)
            y += 15f

            for (ps in data.platformBreakdown) {
                // R&D fix: paginate — never clip off-page, never overwrite footer at y=800.
                if (y > 740f) {
                    document.finishPage(page)
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                    // Repeat the table header on continued pages.
                    canvas.drawText("Platform", colX[0], y, tableHeaderPaint)
                    canvas.drawText("Trips", colX[1], y, tableHeaderPaint)
                    canvas.drawText("Earned", colX[2], y, tableHeaderPaint)
                    canvas.drawText("₹/hr", colX[3], y, tableHeaderPaint)
                    canvas.drawText("Avg Wait", colX[4], y, tableHeaderPaint)
                    canvas.drawText("Distance", colX[5], y, tableHeaderPaint)
                    y += 18f
                    canvas.drawLine(leftMargin, y, 555f, y, linePaint)
                    y += 15f
                }
                canvas.drawText(ps.name, colX[0], y, bodyPaint)
                canvas.drawText("${ps.trips}", colX[1], y, bodyPaint)
                canvas.drawText(String.format(java.util.Locale.US, "₹%.0f", ps.earnings), colX[2], y, bodyPaint)
                canvas.drawText(String.format(java.util.Locale.US, "₹%.0f", ps.netPerHour), colX[3], y, bodyPaint)
                canvas.drawText(String.format(java.util.Locale.US, "%.0fm", ps.avgWaitMinutes), colX[4], y, bodyPaint)
                canvas.drawText(String.format(java.util.Locale.US, "%.1fkm", ps.distanceKm), colX[5], y, bodyPaint)
                y += 18f
            }
        }

        // Footer
        val footerPaint = Paint(bodyPaint).apply {
            textSize = 9f
            color = Color.GRAY
        }
        val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        if (y > 770f) {
            document.finishPage(page)
            val footerPageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
            val footerPage = document.startPage(footerPageInfo)
            footerPage.canvas.drawText("Generated by GigRun v2.0 on $timestamp", leftMargin, 800f, footerPaint)
            document.finishPage(footerPage)
        } else {
            y = 800f
            canvas.drawText("Generated by GigRun v2.0 on $timestamp", leftMargin, y, footerPaint)
            document.finishPage(page)
        }

        // Save to app-specific files directory (R&D fix: null-safe dir, leak-safe close).
        // Random suffix: predictable timestamps let other apps enumerate reports.
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(Date())
        val fileName = "GigRun_Report_${stamp}_${(0..9999).random()}.pdf"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, fileName)
        file.parentFile?.mkdirs()
        try {
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }

        return file
    }

    /**
     * Creates a share intent for the generated PDF file.
     * ClipData carries the grant (not just the extra), filename has a random
     * suffix so reports can't be enumerated by timestamp.
     */
    fun shareReport(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            clipData = android.content.ClipData.newUri(context.contentResolver, "Shift report", uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
