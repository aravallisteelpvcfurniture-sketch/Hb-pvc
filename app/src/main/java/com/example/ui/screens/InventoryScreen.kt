package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryItem
import com.example.data.model.ShopConstants
import com.example.ui.theme.AmberLowStock
import com.example.ui.theme.AmberSurface
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonSurface
import com.example.ui.theme.EmeraldStock
import com.example.ui.theme.EmeraldSurface
import com.example.util.formatInr

@Composable
fun InventoryScreen(
    inventory: List<InventoryItem>,
    isAdmin: Boolean,
    onAdjustStock: (InventoryItem, Long) -> Unit,
    onSaveItem: (InventoryItem) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    var showLowStockOnly by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val totalUnits = inventory.sumOf { it.stockQty }
    val lowOrOutCount = inventory.count { it.isLowStock || it.isOutOfStock }
    val displayed = remember(inventory, showLowStockOnly) {
        if (showLowStockOnly) inventory.filter { it.isLowStock || it.isOutOfStock } else inventory
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Real-Time Stock & Inventory",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (isAdmin) {
                        "Live workshop inventory syncs across catalog & checkout."
                    } else {
                        "Live availability from Aravalli Workshop (Restock edits restricted to shop admin)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InventoryKpiCard("Total SKUs", inventory.size.toString(), Modifier.weight(1f))
                    InventoryKpiCard("Units Ready", totalUnits.toString(), Modifier.weight(1f))
                    InventoryKpiCard(
                        "Low / Out",
                        lowOrOutCount.toString(),
                        Modifier.weight(1f),
                        highlightAlert = lowOrOutCount > 0
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showLowStockOnly,
                        onClick = { showLowStockOnly = false },
                        label = { Text("All Items (${inventory.size})") }
                    )
                    FilterChip(
                        selected = showLowStockOnly,
                        onClick = { showLowStockOnly = true },
                        leadingIcon = if (lowOrOutCount > 0) {
                            { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text("Low Stock ($lowOrOutCount)") }
                    )
                }
            }

            items(displayed, key = { it.id }) { item ->
                InventoryItemCard(
                    item = item,
                    isAdmin = isAdmin,
                    onIncrement = { onAdjustStock(item, 1L) },
                    onDecrement = { onAdjustStock(item, -1L) },
                    onQuickRestock = { onAdjustStock(item, 5L) },
                    onEdit = { editingItem = item },
                    onDelete = { onDeleteItem(item.id) }
                )
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

        if (isAdmin) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_inventory_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add new inventory SKU")
            }
        }
    }

    if (showCreateDialog || editingItem != null) {
        InventoryEditDialog(
            initial = editingItem,
            onDismiss = {
                showCreateDialog = false
                editingItem = null
            },
            onSave = { saved ->
                onSaveItem(saved)
                showCreateDialog = false
                editingItem = null
            }
        )
    }
}

@Composable
private fun InventoryKpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightAlert: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlightAlert) AmberSurface else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (highlightAlert) AmberLowStock else MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    isAdmin: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onQuickRestock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = (item.stockQty.toFloat() / (item.reorderLevel.coerceAtLeast(1L) * 4f)).coerceIn(0f, 1f)
    val (badgeBg, badgeFg, statusText) = when {
        item.isOutOfStock -> Triple(CrimsonSurface, CrimsonAlert, "OUT OF STOCK")
        item.isLowStock -> Triple(AmberSurface, AmberLowStock, "LOW STOCK")
        else -> Triple(EmeraldSurface, EmeraldStock, "HEALTHY STOCK")
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(item.sku, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "${item.category} • ${item.dimensions} • ${formatInr(item.priceInr)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = { progress },
                color = badgeFg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available: ${item.stockQty} units (Min: ${item.reorderLevel})",
                    style = MaterialTheme.typography.labelMedium
                )
                if (isAdmin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDecrement, enabled = item.stockQty > 0) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease stock")
                        }
                        IconButton(onClick = onIncrement) {
                            Icon(Icons.Default.Add, contentDescription = "Increase stock")
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit SKU")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete SKU", tint = CrimsonAlert)
                        }
                    }
                }
            }

            if (isAdmin && (item.isLowStock || item.isOutOfStock)) {
                OutlinedButton(
                    onClick = onQuickRestock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+5 Quick Workshop Restock")
                }
            }
        }
    }
}

@Composable
private fun InventoryEditDialog(
    initial: InventoryItem?,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit
) {
    var sku by remember { mutableStateOf(initial?.sku ?: "ARV-NEW-${(10..99).random()}") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Steel Almirah") }
    var material by remember { mutableStateOf(initial?.material ?: "20-Gauge Powder Coated Steel") }
    var dimensions by remember { mutableStateOf(initial?.dimensions ?: "78 x 36 x 20 in") }
    var priceStr by remember { mutableStateOf(initial?.priceInr?.toInt()?.toString() ?: "18500") }
    var stockStr by remember { mutableStateOf(initial?.stockQty?.toString() ?: "10") }
    var reorderStr by remember { mutableStateOf(initial?.reorderLevel?.toString() ?: "3") }
    var description by remember { mutableStateOf(initial?.description ?: "Workshop fabricated Steel/PVC furniture unit.") }

    val imageKey = when (category) {
        "PVC Kitchen" -> "img_pvc_kitchen"
        "Office & Modular" -> "img_pvc_furniture"
        "UPVC Doors", "Custom Fabrication" -> "img_showroom_hero"
        else -> "img_steel_almirah"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Inventory SKU" else "Edit ${initial.sku}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU Code") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ShopConstants.CATEGORIES.drop(1)) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = material,
                    onValueChange = { material = it },
                    label = { Text("Material Spec") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = dimensions,
                    onValueChange = { dimensions = it },
                    label = { Text("Dimensions") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Price (INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { stockStr = it },
                        label = { Text("Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reorderStr,
                        onValueChange = { reorderStr = it },
                        label = { Text("Min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            val valid = sku.trim().length >= 2 &&
                name.trim().length >= 2 &&
                (priceStr.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (stockStr.toLongOrNull() ?: -1L) >= 0L
            Button(
                onClick = {
                    val saved = (initial ?: InventoryItem()).copy(
                        sku = sku.trim(),
                        name = name.trim(),
                        category = category,
                        material = material.trim(),
                        dimensions = dimensions.trim(),
                        priceInr = priceStr.toDoubleOrNull() ?: 18500.0,
                        stockQty = stockStr.toLongOrNull() ?: 0L,
                        reorderLevel = reorderStr.toLongOrNull() ?: 3L,
                        imageKey = initial?.imageKey ?: imageKey,
                        description = description.trim(),
                        isAvailable = (stockStr.toLongOrNull() ?: 0L) > 0L
                    )
                    onSave(saved)
                },
                enabled = valid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
