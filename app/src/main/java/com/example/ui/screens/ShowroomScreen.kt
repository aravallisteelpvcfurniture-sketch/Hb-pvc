package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FurnitureOrder
import com.example.data.model.InventoryItem
import com.example.data.model.MeasurementRequest
import com.example.data.model.ShopConstants
import com.example.data.model.UserProfile
import com.example.data.preferences.ShopIntegrationSettings
import com.example.ui.theme.CopperTeak
import com.example.ui.theme.SteelCharcoalDark
import com.example.ui.theme.WhatsAppGreen
import com.example.util.BusinessIntegrations
import com.example.util.formatInr

@Composable
fun ShowroomScreen(
    userProfile: UserProfile?,
    isAdmin: Boolean,
    catalog: List<InventoryItem>,
    orders: List<FurnitureOrder>,
    measurements: List<MeasurementRequest>,
    shopSettings: ShopIntegrationSettings,
    onNavigateToCatalog: () -> Unit,
    onNavigateToMeasure: () -> Unit,
    onNavigateToBot: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onSelectFeaturedItem: (InventoryItem) -> Unit,
    onSaveShopSettings: (whatsapp: String, upiId: String) -> Unit,
    onSaveCustomerContact: (name: String, phone: String, address: String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val lowStockCount = catalog.count { it.isLowStock || it.isOutOfStock }
    val activeOrders = orders.count { it.trackingStage != "Delivered" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Showroom Marketing Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_showroom_hero),
                    contentDescription = "Aravalli Steel PVC Furniture Showroom Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    SteelCharcoalDark.copy(alpha = 0.25f),
                                    SteelCharcoalDark.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAdmin) {
                        Surface(
                            color = CopperTeak,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "SHOP ADMIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Workshop Integration Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = ShopConstants.SHOP_NAME,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Custom Steel Almirahs • Waterproof PVC Kitchens • UPVC Doors",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onNavigateToMeasure,
                            colors = ButtonDefaults.buttonColors(containerColor = CopperTeak),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("hero_book_measurement")
                        ) {
                            Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Book Free Visit", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onNavigateToCatalog,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Gallery", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Live Workshop Metrics Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "Active Orders",
                    value = activeOrders.toString(),
                    accent = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToOrders,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Site Visits",
                    value = measurements.size.toString(),
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToMeasure,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = if (lowStockCount > 0) "Low Stock" else "Live SKUs",
                    value = if (lowStockCount > 0) "$lowStockCount Alert" else catalog.size.toString(),
                    accent = if (lowStockCount > 0) MaterialTheme.colorScheme.error else WhatsAppGreen,
                    onClick = onNavigateToInventory,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Core Business Quick Actions
        item {
            Text(
                text = "Quick Workshop Actions",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    title = "AI Order Bot",
                    subtitle = "Quote & place order",
                    icon = Icons.Default.SmartToy,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToBot,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_ai_bot")
                )
                QuickActionCard(
                    title = "Photo Gallery",
                    subtitle = "${catalog.size} Steel & PVC units",
                    icon = Icons.Default.PhotoLibrary,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToCatalog,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_gallery")
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    title = "Live WhatsApp",
                    subtitle = "Instant workshop chat",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    tint = WhatsAppGreen,
                    onClick = {
                        BusinessIntegrations.openWhatsAppSupport(
                            context = context,
                            phoneDigits = shopSettings.whatsappNumber,
                            message = "Namaste Aravalli Steel PVC Furniture! I would like an estimate / support for custom furniture."
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_whatsapp")
                )
                QuickActionCard(
                    title = "Stock & Orders",
                    subtitle = "PDF Bills & Tracking",
                    icon = Icons.Default.Build,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToOrders,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_orders")
                )
            }
        }

        // Featured Catalog Showcase
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Signature Steel & PVC Collection",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onNavigateToCatalog) {
                    Text("View All")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(catalog.take(5), key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .width(230.dp)
                            .clickable { onSelectFeaturedItem(item) }
                    ) {
                        Column {
                            Image(
                                painter = painterResource(id = ShopConstants.drawableForKey(item.imageKey)),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatInr(item.priceInr),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (item.isOutOfStock) "Out" else "${item.stockQty} left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.isOutOfStock) MaterialTheme.colorScheme.error else WhatsAppGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Why Aravalli Craftsmanship & Account Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Workshop Guarantee & Customer Profile",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Signed in as ${userProfile?.displayName ?: "Customer"} (${userProfile?.email ?: ""})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!userProfile?.address.isNullOrBlank()) {
                        Text(
                            text = "Default Address: ${userProfile?.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Edit Contact")
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        var whatsappInput by remember { mutableStateOf(shopSettings.whatsappNumber) }
        var upiInput by remember { mutableStateOf(shopSettings.upiId) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Workshop Integration Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Configure your shop's live WhatsApp Support number & UPI Payment ID.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = whatsappInput,
                        onValueChange = { whatsappInput = it },
                        label = { Text("WhatsApp Business Number (with 91)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = upiInput,
                        onValueChange = { upiInput = it },
                        label = { Text("Shop UPI VPA ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveShopSettings(whatsappInput, upiInput)
                        showSettingsDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showProfileDialog) {
        var nameInput by remember { mutableStateOf(userProfile?.displayName.orEmpty()) }
        var phoneInput by remember { mutableStateOf(userProfile?.phone.orEmpty()) }
        var addrInput by remember { mutableStateOf(userProfile?.address.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Default Contact & Site Address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Mobile / WhatsApp Number") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = addrInput,
                        onValueChange = { addrInput = it },
                        label = { Text("Delivery / Site Address") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomerContact(nameInput, phoneInput, addrInput)
                        showProfileDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = tint.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
