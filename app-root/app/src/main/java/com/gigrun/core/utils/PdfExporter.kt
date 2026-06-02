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
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

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
        drawRow("Total Distance", String.format("%.1f km", data.totalDistanceKm))
        drawRow("Shift Time", "${data.totalShiftTimeMinutes / 60}h ${data.totalShiftTimeMinutes % 60}m")
        drawRow("Riding Time", "${data.totalRidingTimeMinutes / 60}h ${data.totalRidingTimeMinutes % 60}m")
        drawRow("Unpaid Wait Time", "${data.totalWaitTimeMinutes / 60}h ${data.totalWaitTimeMinutes % 60}m")
        y += 10f
        canvas.drawLine(leftMargin, y, 555f, y, linePaint)
        y += 25f

        // Earnings Section
        canvas.drawText("EARNINGS", leftMargin, y, headerPaint)
        y += 25f
        drawRow("Gross Earnings", String.format("₹ %.0f", data.grossEarnings))
        drawRow("Fuel Cost", String.format("₹ %.0f", data.fuelCost))
        drawRow("Net Earnings", String.format("₹ %.0f", data.netEarnings))
        drawRow("Gross ₹/hour", String.format("₹ %.0f/hr", data.grossPerHour))
        drawRow("Net ₹/hour", String.format("₹ %.0f/hr", data.netPerHour))
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
                canvas.drawText(ps.name, colX[0], y, bodyPaint)
                canvas.drawText("${ps.trips}", colX[1], y, bodyPaint)
                canvas.drawText(String.format("₹%.0f", ps.earnings), colX[2], y, bodyPaint)
                canvas.drawText(String.format("₹%.0f", ps.netPerHour), colX[3], y, bodyPaint)
                canvas.drawText(String.format("%.0fm", ps.avgWaitMinutes), colX[4], y, bodyPaint)
                canvas.drawText(String.format("%.1fkm", ps.distanceKm), colX[5], y, bodyPaint)
                y += 18f
            }
        }

        // Footer
        y = 800f
        val footerPaint = Paint(bodyPaint).apply {
            textSize = 9f
            color = Color.GRAY
        }
        val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Generated by GigRun v2.0 on $timestamp", leftMargin, y, footerPaint)

        document.finishPage(page)

        // Save to app-specific files directory
        val fileName = "GigRun_Report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return file
    }

    /**
     * Creates a share intent for the generated PDF file.
     */
    fun shareReport(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
