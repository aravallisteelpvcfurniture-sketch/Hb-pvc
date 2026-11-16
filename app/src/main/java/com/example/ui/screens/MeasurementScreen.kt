package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.MeasurementRequest
import com.example.ui.theme.AmberLowStock
import com.example.ui.theme.AmberSurface
import com.example.ui.theme.EmeraldStock
import com.example.ui.theme.EmeraldSurface
import com.example.ui.viewmodel.RoomAnalysisState
import com.example.util.BusinessIntegrations
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MeasurementScreen(
    measurements: List<MeasurementRequest>,
    isAdmin: Boolean,
    initialName: String,
    initialPhone: String,
    initialAddress: String,
    prefilledFurnitureType: String?,
    roomAnalysisState: RoomAnalysisState,
    whatsappNumber: String,
    onAnalyzeRoomPhoto: (Bitmap, String) -> Unit,
    onClearRoomAnalysis: () -> Unit,
    onSubmitMeasurement: (
        customerName: String,
        phone: String,
        address: String,
        furnitureType: String,
        roomArea: String,
        widthFt: Double,
        heightFt: Double,
        depthFt: Double,
        preferredDate: String,
        preferredSlot: String,
        notes: String?
    ) -> Unit,
    onAdvanceStatus: (MeasurementRequest) -> Unit,
    onConvertToOrder: (MeasurementRequest) -> Unit
) {
    val context = LocalContext.current

    val defaultDate = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    var customerName by remember(initialName) { mutableStateOf(initialName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var address by remember(initialAddress) { mutableStateOf(initialAddress) }
    var furnitureType by remember(prefilledFurnitureType) {
        mutableStateOf(prefilledFurnitureType ?: "Steel Almirah")
    }
    var roomArea by remember { mutableStateOf("Master Bedroom") }
    var widthStr by remember { mutableStateOf("6.5") }
    var heightStr by remember { mutableStateOf("7.0") }
    var depthStr by remember { mutableStateOf("2.0") }
    var preferredDate by remember { mutableStateOf(defaultDate) }
    var preferredSlot by remember { mutableStateOf("10:00 AM - 12:00 PM") }
    var notes by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val furnitureTypes = listOf("Steel Almirah", "PVC Kitchen", "UPVC Doors", "Office & Modular", "Custom Fabrication")
    val slots = listOf("10:00 AM - 12:00 PM", "12:00 PM - 03:00 PM", "03:00 PM - 06:00 PM")

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val rawBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            // Downscale for fast, under-20MB AI multimodal analysis
            val maxDim = 1024
            val scale = minOf(maxDim.toFloat() / rawBmp.width, maxDim.toFloat() / rawBmp.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rawBmp,
                    (rawBmp.width * scale).toInt().coerceAtLeast(1),
                    (rawBmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                rawBmp
            }
            selectedBitmap = scaled
            onClearRoomAnalysis()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Visitor Site Measurement",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Schedule a technician visit or analyze your wall/kitchen space before placing a custom order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Booking Form Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Furniture Category", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(furnitureTypes) { type ->
                            FilterChip(
                                selected = furnitureType == type,
                                onClick = { furnitureType = type },
                                label = { Text(type) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Visitor / Customer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Mobile Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = roomArea,
                            onValueChange = { roomArea = it },
                            label = { Text("Room / Area") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Site Address") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Estimated Dimensions (Feet)", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = widthStr,
                            onValueChange = { widthStr = it },
                            label = { Text("Width (ft)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightStr,
                            onValueChange = { heightStr = it },
                            label = { Text("Height (ft)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = depthStr,
                            onValueChange = { depthStr = it },
                            label = { Text("Depth (ft)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = preferredDate,
                            onValueChange = { preferredDate = it },
                            label = { Text("Preferred Date") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(slots) { slot ->
                            FilterChip(
                                selected = preferredSlot == slot,
                                onClick = { preferredSlot = slot },
                                label = { Text(slot) }
                            )
                        }
                    }

                    // Zero-Permission Photo Picker + AI Room Photo Advisor
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Room / Wall Photo (Optional AI Sizing)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (selectedBitmap == null) "Pick Photo" else "Change")
                                }
                            }

                            selectedBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Selected room wall photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Button(
                                    onClick = { onAnalyzeRoomPhoto(bmp, furnitureType) },
                                    enabled = roomAnalysisState !is RoomAnalysisState.Analyzing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (roomAnalysisState is RoomAnalysisState.Analyzing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Analyzing Room Space...")
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Analyze Room Photo with AI")
                                    }
                                }
                            }

                            when (roomAnalysisState) {
                                is RoomAnalysisState.Success -> {
                                    Surface(
                                        color = EmeraldSurface,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = roomAnalysisState.recommendation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EmeraldStock,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                is RoomAnalysisState.Error -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = roomAnalysisState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Custom Finish / Notes (Optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val width = widthStr.toDoubleOrNull() ?: 0.0
                    val height = heightStr.toDoubleOrNull() ?: 0.0
                    val depth = depthStr.toDoubleOrNull() ?: 0.0
                    val isValid = customerName.isNotBlank() &&
                        phone.trim().length >= 6 &&
                        address.trim().length >= 3 &&
                        width > 0.0 && height > 0.0 && depth > 0.0

                    Button(
                        onClick = {
                            val combinedNotes = buildString {
                                if (notes.isNotBlank()) append(notes.trim())
                                if (roomAnalysisState is RoomAnalysisState.Success) {
                                    if (isNotEmpty()) append(" | ")
                                    append("AI Tip: ${roomAnalysisState.recommendation.take(180)}")
                                }
                            }.ifBlank { null }

                            onSubmitMeasurement(
                                customerName.trim(),
                                phone.trim(),
                                address.trim(),
                                furnitureType,
                                roomArea.trim().ifBlank { "Room" },
                                width,
                                height,
                                depth,
                                preferredDate.trim(),
                                preferredSlot,
                                combinedNotes
                            )
                            notes = ""
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_measurement_button")
                    ) {
                        Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Confirm Measurement Visit")
                    }
                }
            }
        }

        // Scheduled Visits List
        item {
            Text(
                text = "Scheduled Visits (${measurements.size})",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (measurements.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("No measurements booked yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Submit the form above so our workshop technician can verify exact dimensions on-site.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(measurements, key = { it.id }) { m ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.furnitureType, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${m.roomArea} • ${m.widthFt}ft x ${m.heightFt}ft x ${m.depthFt}ft",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Surface(
                                color = if (m.status == "converted") EmeraldSurface else AmberSurface,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = m.status.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (m.status == "converted") EmeraldStock else AmberLowStock,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "${m.customerName} (${m.phone}) • ${m.address}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Visit: ${m.preferredDate} • ${m.preferredSlot}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!m.notes.isNullOrBlank()) {
                            Text(
                                text = "Notes: ${m.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onConvertToOrder(m) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Place Order")
                            }
                            OutlinedButton(
                                onClick = {
                                    BusinessIntegrations.openWhatsAppSupport(
                                        context = context,
                                        phoneDigits = whatsappNumber,
                                        message = "Measurement visit inquiry for ${m.customerName}: ${m.furnitureType} (${m.widthFt}x${m.heightFt}x${m.depthFt} ft) on ${m.preferredDate} ${m.preferredSlot} at ${m.address}."
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("WhatsApp Visit")
                            }
                        }
                        if (isAdmin && m.status != "converted") {
                            OutlinedButton(
                                onClick = { onAdvanceStatus(m) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Admin: Mark Next Status")
                            }
                        }
                    }
                }
            }
        }
    }
}
