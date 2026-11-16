package com.example.ui.components

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FurnitureOrder
import com.example.data.model.ShopConstants
import com.example.ui.theme.EmeraldStock
import com.example.ui.theme.EmeraldSurface
import com.example.util.BusinessIntegrations
import com.example.util.PdfInvoiceGenerator
import com.example.util.formatInr
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCheckoutSheet(
    itemName: String,
    itemSku: String,
    defaultDimensions: String,
    unitPriceInr: Double,
    maxStock: Long,
    initialCustomerName: String,
    initialPhone: String,
    initialAddress: String,
    initialNotes: String,
    upiId: String,
    inventoryItemId: String?,
    onDismiss: () -> Unit,
    onConfirmOrder: (
        customerName: String,
        phone: String,
        address: String,
        dimensions: String,
        quantity: Long,
        paymentMethod: String,
        paymentStatus: String,
        paymentRef: String?,
        notes: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var customerName by remember { mutableStateOf(initialCustomerName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    var dimensions by remember { mutableStateOf(defaultDimensions) }
    var notes by remember { mutableStateOf(initialNotes) }
    var quantity by remember { mutableIntStateOf(1) }
    var selectedMethod by remember { mutableStateOf("UPI") }
    var paymentRef by remember { mutableStateOf("") }
    var upiVerified by remember { mutableStateOf(false) }
    var gatewayMessage by remember { mutableStateOf<String?>(null) }

    val maxAllowed = if (maxStock > 0L) maxStock.toInt().coerceAtMost(20) else 20
    val subtotal = unitPriceInr * quantity
    val gst = subtotal * ShopConstants.GST_RATE
    val total = subtotal + gst

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.data != null) {
            val parsed = BusinessIntegrations.parseUpiResponse(result.data)
            if (parsed.isSuccess) {
                upiVerified = true
                paymentRef = parsed.txnRef
                gatewayMessage = "UPI Payment Verified (${parsed.txnRef})"
            } else {
                gatewayMessage = parsed.message
            }
        } else {
            gatewayMessage = "UPI app closed. You can enter a UTR reference or select COD."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirm Order & Payment",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "$itemName ($itemSku)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close checkout")
                }
            }

            // Quantity & Dimensions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dimensions,
                    onValueChange = { dimensions = it },
                    label = { Text("Dimensions (W x H x D)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity")
                        }
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { if (quantity < maxAllowed) quantity++ },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase quantity")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Customer / Billing Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("WhatsApp / Mobile Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Delivery & Installation Address") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Finish / Color / Mirror Notes (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Payment Gateway Selector
            Text(
                text = "Secure Payment Gateway",
                style = MaterialTheme.typography.titleSmall
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethodOption(
                    title = "UPI Instant Pay (GPay / PhonePe / Paytm)",
                    subtitle = "Pay directly to $upiId",
                    icon = Icons.Default.AccountBalanceWallet,
                    selected = selectedMethod == "UPI",
                    onClick = { selectedMethod = "UPI" }
                )
                PaymentMethodOption(
                    title = "Card / NetBanking Gateway",
                    subtitle = "Enter gateway authorization reference",
                    icon = Icons.Default.CreditCard,
                    selected = selectedMethod == "Card/NetBanking",
                    onClick = { selectedMethod = "Card/NetBanking" }
                )
                PaymentMethodOption(
                    title = "Cash / UPI on Delivery",
                    subtitle = "Confirm order now & pay after installation",
                    icon = Icons.Default.LocalShipping,
                    selected = selectedMethod == "Cash on Delivery",
                    onClick = { selectedMethod = "Cash on Delivery" }
                )
            }

            if (selectedMethod == "UPI") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Shop UPI ID", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = upiId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    BusinessIntegrations.copyToClipboard(context, "UPI ID", upiId)
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val upiIntent = BusinessIntegrations.buildUpiPaymentIntent(
                                    upiId = upiId,
                                    payeeName = ShopConstants.SHOP_NAME,
                                    transactionRef = "ARV${System.currentTimeMillis() % 1000000}",
                                    note = "Order $itemSku",
                                    amountInr = total
                                )
                                if (BusinessIntegrations.hasUpiHandler(context, upiIntent)) {
                                    upiLauncher.launch(upiIntent)
                                } else {
                                    gatewayMessage = "No UPI app installed on this emulator. Enter UTR below or place as Pending / COD."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Launch UPI App to Pay ${formatInr(total)}")
                        }

                        OutlinedTextField(
                            value = paymentRef,
                            onValueChange = { paymentRef = it },
                            label = { Text("UPI UTR / Reference (Optional if paying later)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (selectedMethod == "Card/NetBanking") {
                OutlinedTextField(
                    value = paymentRef,
                    onValueChange = { paymentRef = it },
                    label = { Text("Gateway Transaction / Card Ref #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!gatewayMessage.isNullOrBlank()) {
                Surface(
                    color = if (upiVerified) EmeraldSurface else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = gatewayMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (upiVerified) EmeraldStock else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // GST Breakdown
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Taxable Subtotal ($quantity unit)", style = MaterialTheme.typography.bodyMedium)
                        Text(formatInr(subtotal), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CGST (9%) + SGST (9%)", style = MaterialTheme.typography.bodyMedium)
                        Text(formatInr(gst), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total (Digital PDF Bill)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = formatInr(total),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val formValid = customerName.isNotBlank() && phone.trim().length >= 6 && address.trim().length >= 3
            Button(
                onClick = {
                    val paymentStatus = when (selectedMethod) {
                        "UPI" -> if (upiVerified || paymentRef.trim().length >= 6) "paid_upi" else "pending"
                        "Card/NetBanking" -> if (paymentRef.trim().length >= 6) "paid_gateway" else "pending"
                        else -> "cod_pending"
                    }
                    onConfirmOrder(
                        customerName.trim(),
                        phone.trim(),
                        address.trim(),
                        dimensions.trim().ifBlank { defaultDimensions },
                        quantity.toLong(),
                        selectedMethod,
                        paymentStatus,
                        paymentRef.trim().ifBlank { null },
                        notes.trim().ifBlank { null }
                    )
                },
                enabled = formValid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_order_button")
            ) {
                Text("Confirm Order & Generate PDF Bill")
            }
        }
    }
}

@Composable
private fun PaymentMethodOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun PdfBillPreviewDialog(
    order: FurnitureOrder,
    whatsappNumber: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pdfFile by remember(order.id, order.orderNumber, order.paymentStatus, order.trackingStage) {
        mutableStateOf<File?>(null)
    }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(order) {
        val generated = PdfInvoiceGenerator.generateInvoicePdf(context, order)
        pdfFile = generated
        pageBitmaps = PdfInvoiceGenerator.renderPdfPreviewBitmaps(generated)
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = pdfFile
        if (uri != null && file != null) {
            val saved = PdfInvoiceGenerator.copyPdfToUri(context, file, uri)
            Toast.makeText(
                context,
                if (saved) "PDF Invoice saved to device!" else "Could not save PDF.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Digital GST Bill (PDF)",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = order.orderNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close PDF preview")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pageBitmaps.forEachIndexed { idx, bmp ->
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Invoice PDF page ${idx + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            savePdfLauncher.launch("Invoice_${order.orderNumber}.pdf")
                        },
                        enabled = pdfFile != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save PDF")
                    }
                    Button(
                        onClick = {
                            pdfFile?.let { PdfInvoiceGenerator.sharePdfInvoice(context, it, order) }
                        },
                        enabled = pdfFile != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share Bill")
                    }
                }
                OutlinedButton(
                    onClick = {
                        BusinessIntegrations.openWhatsAppSupport(
                            context = context,
                            phoneDigits = whatsappNumber,
                            message = "Hello Aravalli Steel PVC Furniture, sharing query for Invoice ${order.orderNumber} (${order.itemSummary}, Total ${formatInr(order.totalInr)})."
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Text("Send Bill Reference on Live WhatsApp Support")
                }
            }
        }
    }
}
