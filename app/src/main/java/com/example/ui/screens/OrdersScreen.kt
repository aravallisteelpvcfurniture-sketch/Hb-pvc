package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FurnitureOrder
import com.example.data.model.ShopConstants
import com.example.data.model.TrackingStage
import com.example.ui.components.PdfBillPreviewDialog
import com.example.ui.theme.AmberLowStock
import com.example.ui.theme.AmberSurface
import com.example.ui.theme.EmeraldStock
import com.example.ui.theme.EmeraldSurface
import com.example.util.BusinessIntegrations
import com.example.util.formatInr

@Composable
fun OrdersScreen(
    orders: List<FurnitureOrder>,
    isAdmin: Boolean,
    upiId: String,
    whatsappNumber: String,
    onAdvanceStage: (FurnitureOrder) -> Unit,
    onCompletePayment: (orderId: String, method: String, status: String, ref: String) -> Unit
) {
    val context = LocalContext.current
    var pdfOrder by remember { mutableStateOf<FurnitureOrder?>(null) }
    var payingOrder by remember { mutableStateOf<FurnitureOrder?>(null) }
    var utrInput by remember { mutableStateOf("") }

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val currentOrder = payingOrder
        if (currentOrder != null && (result.resultCode == Activity.RESULT_OK || result.data != null)) {
            val parsed = BusinessIntegrations.parseUpiResponse(result.data)
            if (parsed.isSuccess) {
                onCompletePayment(currentOrder.id, "UPI", "paid_upi", parsed.txnRef)
                payingOrder = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Automated Order Tracking & Bills",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Live workshop fabrication stages, instant PDF GST bills, UPI settlement & WhatsApp support.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (orders.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text("No orders confirmed yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Browse the Catalog Photo Gallery or ask the AI Order Bot to place your first Steel/PVC order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(orders, key = { it.id }) { order ->
                OrderTrackingCard(
                    order = order,
                    isAdmin = isAdmin,
                    onViewPdfBill = { pdfOrder = order },
                    onWhatsAppSupport = {
                        BusinessIntegrations.openWhatsAppSupport(
                            context = context,
                            phoneDigits = whatsappNumber,
                            message = "Hi Aravalli Workshop, checking live status for Order ${order.orderNumber} (${order.itemSummary}, Stage: ${order.trackingStage})."
                        )
                    },
                    onPayPending = {
                        utrInput = ""
                        payingOrder = order
                    },
                    onAdvanceStage = { onAdvanceStage(order) }
                )
            }
        }
    }

    if (pdfOrder != null) {
        PdfBillPreviewDialog(
            order = pdfOrder!!,
            whatsappNumber = whatsappNumber,
            onDismiss = { pdfOrder = null }
        )
    }

    if (payingOrder != null) {
        val target = payingOrder!!
        AlertDialog(
            onDismissRequest = { payingOrder = null },
            title = { Text("Complete Payment (${target.orderNumber})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Amount Due: ${formatInr(target.totalInr)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = BusinessIntegrations.buildUpiPaymentIntent(
                                upiId = upiId,
                                payeeName = ShopConstants.SHOP_NAME,
                                transactionRef = target.orderNumber,
                                note = "Invoice ${target.orderNumber}",
                                amountInr = target.totalInr
                            )
                            if (BusinessIntegrations.hasUpiHandler(context, intent)) {
                                upiLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open UPI App (${upiId})")
                    }
                    OutlinedTextField(
                        value = utrInput,
                        onValueChange = { utrInput = it },
                        label = { Text("Enter UPI UTR / Gateway Ref #") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCompletePayment(
                            target.id,
                            "UPI",
                            "paid_upi",
                            utrInput.trim()
                        )
                        payingOrder = null
                    },
                    enabled = utrInput.trim().length >= 6
                ) {
                    Text("Verify Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { payingOrder = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun OrderTrackingCard(
    order: FurnitureOrder,
    isAdmin: Boolean,
    onViewPdfBill: () -> Unit,
    onWhatsAppSupport: () -> Unit,
    onPayPending: () -> Unit,
    onAdvanceStage: () -> Unit
) {
    val currentStageIdx = TrackingStage.indexOfLabel(order.trackingStage)
    val isPaid = order.paymentStatus == "paid_upi" || order.paymentStatus == "paid_gateway"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.orderNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = order.itemSummary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${order.customDimensions} • Qty ${order.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatInr(order.totalInr),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = if (isPaid) EmeraldSurface else AmberSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when (order.paymentStatus) {
                                "paid_upi" -> "PAID UPI"
                                "paid_gateway" -> "PAID GATEWAY"
                                "cod_pending" -> "COD"
                                else -> "PAY PENDING"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPaid) EmeraldStock else AmberLowStock,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Automated 6-Stage Order Stepper
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Automated Tracking • Est. ${order.estimatedDays} days",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                TrackingStage.orderedStages.forEachIndexed { idx, stage ->
                    val completed = idx <= currentStageIdx
                    val isCurrent = idx == currentStageIdx
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (completed) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stage.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isCurrent) {
                                Text(
                                    text = stage.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onViewPdfBill,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pdf_bill_${order.orderNumber}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF Bill")
                }
                OutlinedButton(
                    onClick = onWhatsAppSupport,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("WhatsApp Track")
                }
            }

            if (!isPaid && order.trackingStage != "Delivered") {
                OutlinedButton(
                    onClick = onPayPending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pay Now via UPI / Gateway")
                }
            }

            if (isAdmin && order.trackingStage != "Delivered") {
                OutlinedButton(
                    onClick = onAdvanceStage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Admin: Advance to ${TrackingStage.nextStageLabel(order.trackingStage)}")
                }
            }
        }
    }
}
