package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.example.data.model.FurnitureOrder
import com.example.data.model.ShopConstants
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfInvoiceGenerator {

    private const val PAGE_WIDTH = 595 // A4 points at 72 dpi
    private const val PAGE_HEIGHT = 842

    fun generateInvoicePdf(context: Context, order: FurnitureOrder): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeName = order.orderNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "Invoice_$safeName.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawInvoicePage(page.canvas, order)
        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    fun renderPdfPreviewBitmaps(file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bmp)
                    }
                }
            }
        }
        return bitmaps
    }

    fun copyPdfToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    fun sharePdfInvoice(context: Context, file: File, order: FurnitureOrder) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Tax Invoice ${order.orderNumber} - ${ShopConstants.SHOP_NAME}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Dear ${order.customerName}, please find your digital GST invoice (${order.orderNumber}) from ${ShopConstants.SHOP_NAME}. Total: ${formatInr(order.totalInr)}."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share GST Invoice PDF"))
    }

    private fun drawInvoicePage(canvas: Canvas, order: FurnitureOrder) {
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        val headerBg = Paint().apply { color = Color.parseColor("#1E293B") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 132f, headerBg)

        val copperAccent = Paint().apply { color = Color.parseColor("#C26D2D") }
        canvas.drawRect(0f, 128f, PAGE_WIDTH.toFloat(), 134f, copperAccent)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 10f
            isAntiAlias = true
        }
        val tagPaint = Paint().apply {
            color = Color.parseColor("#F59E0B")
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        canvas.drawText(ShopConstants.SHOP_NAME.uppercase(), 36f, 46f, titlePaint)
        canvas.drawText(ShopConstants.SHOP_TAGLINE, 36f, 66f, subtitlePaint)
        canvas.drawText(ShopConstants.SHOP_ADDRESS, 36f, 84f, subtitlePaint)
        canvas.drawText("GSTIN: ${ShopConstants.SHOP_GSTIN}  |  WhatsApp: +${ShopConstants.DEFAULT_WHATSAPP_NUMBER}", 36f, 102f, subtitlePaint)

        canvas.drawText("TAX INVOICE", PAGE_WIDTH - 36f, 46f, tagPaint)
        val invoiceNoPaint = Paint(subtitlePaint).apply {
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
            textSize = 11f
        }
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(
            order.createdAt?.toDate() ?: Date()
        )
        canvas.drawText("Invoice #: ${order.orderNumber}", PAGE_WIDTH - 36f, 68f, invoiceNoPaint)
        canvas.drawText("Date: $dateStr", PAGE_WIDTH - 36f, 86f, invoiceNoPaint)

        // Billing & Order Meta Box
        val labelPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            isAntiAlias = true
        }
        val boldBodyPaint = Paint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val cardPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val infoBox = RectF(36f, 156f, PAGE_WIDTH - 36f, 268f)
        canvas.drawRoundRect(infoBox, 8f, 8f, cardPaint)
        canvas.drawRoundRect(infoBox, 8f, 8f, borderPaint)

        canvas.drawText("BILLED TO", 52f, 178f, labelPaint)
        canvas.drawText(order.customerName, 52f, 196f, boldBodyPaint)
        canvas.drawText("Phone: ${order.phone}", 52f, 214f, bodyPaint)
        drawWrappedText(canvas, "Address: ${order.deliveryAddress}", 52f, 232f, 240f, 14f, bodyPaint)

        canvas.drawText("ORDER & TRACKING STATUS", 320f, 178f, labelPaint)
        canvas.drawText("Stage: ${order.trackingStage}", 320f, 196f, boldBodyPaint)
        canvas.drawText("Payment: ${order.paymentMethod} (${paymentBadgeLabel(order.paymentStatus)})", 320f, 214f, bodyPaint)
        if (!order.paymentRef.isNullOrBlank()) {
            canvas.drawText("Txn Ref: ${order.paymentRef}", 320f, 232f, bodyPaint)
        } else {
            canvas.drawText("Est. Completion: ${order.estimatedDays} working days", 320f, 232f, bodyPaint)
        }

        // Line Items Table Header
        val tableTop = 292f
        val tableHeaderPaint = Paint().apply { color = Color.parseColor("#1E293B") }
        canvas.drawRect(36f, tableTop, PAGE_WIDTH - 36f, tableTop + 28f, tableHeaderPaint)

        val thPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("SKU / DESCRIPTION", 48f, tableTop + 18f, thPaint)
        canvas.drawText("DIMENSIONS", 295f, tableTop + 18f, thPaint)
        canvas.drawText("QTY", 415f, tableTop + 18f, thPaint)
        val thRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("AMOUNT (INR)", PAGE_WIDTH - 48f, tableTop + 18f, thRight)

        // Line Row
        val rowTop = tableTop + 28f
        val rowBottom = rowTop + 86f
        canvas.drawRect(36f, rowTop, PAGE_WIDTH - 36f, rowBottom, borderPaint)

        canvas.drawText(order.itemSku, 48f, rowTop + 20f, labelPaint)
        drawWrappedText(canvas, order.itemSummary, 48f, rowTop + 36f, 230f, 14f, boldBodyPaint)
        drawWrappedText(canvas, order.customDimensions, 295f, rowTop + 28f, 105f, 14f, bodyPaint)
        canvas.drawText("${order.quantity}", 422f, rowTop + 28f, boldBodyPaint)

        val amountRight = Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(formatInr(order.subtotalInr), PAGE_WIDTH - 48f, rowTop + 28f, amountRight)

        if (!order.notes.isNullOrBlank()) {
            canvas.drawText("Workshop Notes: ${order.notes.take(80)}", 48f, rowBottom - 12f, labelPaint)
        }

        // Tax & Total Summary
        val summaryTop = rowBottom + 24f
        val summaryLeft = 320f
        val summaryRight = PAGE_WIDTH - 36f
        val cgst = order.gstInr / 2.0
        val sgst = order.gstInr / 2.0

        val rightBody = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Taxable Subtotal:", summaryLeft, summaryTop, bodyPaint)
        canvas.drawText(formatInr(order.subtotalInr), summaryRight, summaryTop, rightBody)

        canvas.drawText("CGST (9%):", summaryLeft, summaryTop + 22f, bodyPaint)
        canvas.drawText(formatInr(cgst), summaryRight, summaryTop + 22f, rightBody)

        canvas.drawText("SGST (9%):", summaryLeft, summaryTop + 44f, bodyPaint)
        canvas.drawText(formatInr(sgst), summaryRight, summaryTop + 44f, rightBody)

        val totalBox = RectF(summaryLeft - 12f, summaryTop + 58f, summaryRight, summaryTop + 96f)
        canvas.drawRoundRect(totalBox, 6f, 6f, headerBg)
        val totalLabelPaint = Paint(thPaint).apply { textSize = 12f }
        val totalValPaint = Paint(thPaint).apply {
            textSize = 14f
            textAlign = Paint.Align.RIGHT
            color = Color.parseColor("#F59E0B")
        }
        canvas.drawText("GRAND TOTAL", summaryLeft, summaryTop + 82f, totalLabelPaint)
        canvas.drawText(formatInr(order.totalInr), summaryRight - 10f, summaryTop + 82f, totalValPaint)

        // Terms & Footer
        val footerY = 720f
        canvas.drawLine(36f, footerY, PAGE_WIDTH - 36f, footerY, borderPaint)
        canvas.drawText("WARRANTY & TERMS", 36f, footerY + 20f, labelPaint)
        canvas.drawText("1. 5-Year structural warranty on CRCA Steel & 10-Year waterproof guarantee on Virgin PVC boards.", 36f, footerY + 38f, subtitlePaint.apply { color = Color.parseColor("#475569") })
        canvas.drawText("2. Custom dimension tolerances +/- 2mm per industrial fabrication standards.", 36f, footerY + 54f, subtitlePaint)
        canvas.drawText("3. Computer-generated digital tax invoice by Aravalli Steel PVC Furniture.", 36f, footerY + 70f, subtitlePaint)

        val signPaint = Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Authorized Signatory", PAGE_WIDTH - 36f, footerY + 56f, signPaint)
        canvas.drawText("Aravalli Steel PVC Furniture", PAGE_WIDTH - 36f, footerY + 72f, labelPaint.apply { textAlign = Paint.Align.RIGHT })
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ) {
        val words = text.split(" ")
        var line = StringBuilder()
        var y = startY
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                line = StringBuilder(word)
                y += lineHeight
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, y, paint)
        }
    }

    private fun paymentBadgeLabel(status: String): String = when (status) {
        "paid_upi" -> "Paid via UPI"
        "paid_gateway" -> "Paid via Gateway"
        "cod_pending" -> "Cash on Delivery"
        else -> "Payment Pending"
    }
}
