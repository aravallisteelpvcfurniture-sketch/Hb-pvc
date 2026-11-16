package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ShopConstants
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

data class UpiPaymentResult(
    val isSuccess: Boolean,
    val status: String,
    val txnRef: String,
    val message: String
)

fun formatInr(amount: Double): String {
    val indiaLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    val formatter = NumberFormat.getCurrencyInstance(indiaLocale)
    formatter.maximumFractionDigits = 0
    return formatter.format(amount)
}

object BusinessIntegrations {

    fun openWhatsAppSupport(
        context: Context,
        phoneDigits: String,
        message: String
    ) {
        val cleanPhone = phoneDigits.filter { it.isDigit() }.ifBlank { ShopConstants.DEFAULT_WHATSAPP_NUMBER }
        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$cleanPhone?text=$encodedMessage")

        val whatsappIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
        }
        val businessIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp.w4b")
        }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)

        val pm = context.packageManager
        val chosen = when {
            whatsappIntent.resolveActivity(pm) != null -> whatsappIntent
            businessIntent.resolveActivity(pm) != null -> businessIntent
            else -> fallbackIntent
        }

        try {
            context.startActivity(chosen)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Unable to open WhatsApp or browser on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun buildUpiPaymentIntent(
        upiId: String,
        payeeName: String,
        transactionRef: String,
        note: String,
        amountInr: Double
    ): Intent {
        val formattedAmount = String.format(Locale.US, "%.2f", amountInr)
        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId.ifBlank { ShopConstants.DEFAULT_UPI_ID })
            .appendQueryParameter("pn", payeeName)
            .appendQueryParameter("tr", transactionRef)
            .appendQueryParameter("tn", note)
            .appendQueryParameter("am", formattedAmount)
            .appendQueryParameter("cu", "INR")
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun hasUpiHandler(context: Context, intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }

    fun parseUpiResponse(data: Intent?): UpiPaymentResult {
        val response = data?.getStringExtra("response").orEmpty()
        if (response.isBlank()) {
            return UpiPaymentResult(
                isSuccess = false,
                status = "CANCELLED",
                txnRef = "",
                message = "Payment flow returned without confirmation."
            )
        }
        val params = response.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                part.substring(0, idx).lowercase(Locale.US) to part.substring(idx + 1)
            } else {
                null
            }
        }.toMap()

        val status = params["status"]?.uppercase(Locale.US) ?: "FAILURE"
        val txnRef = params["txnref"] ?: params["approvalrefno"] ?: ""
        return when (status) {
            "SUCCESS" -> UpiPaymentResult(
                isSuccess = true,
                status = "SUCCESS",
                txnRef = txnRef.ifBlank { "UPI-${System.currentTimeMillis() % 1000000}" },
                message = "UPI payment verified."
            )
            "SUBMITTED" -> UpiPaymentResult(
                isSuccess = false,
                status = "PENDING",
                txnRef = txnRef,
                message = "Payment submitted and awaiting bank confirmation."
            )
            else -> UpiPaymentResult(
                isSuccess = false,
                status = status,
                txnRef = txnRef,
                message = "UPI payment was cancelled or failed."
            )
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
}
