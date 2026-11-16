package com.example.data.model

import androidx.annotation.DrawableRes
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val role: String = "customer",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val sku: String = "",
    val name: String = "",
    val category: String = "Steel Almirah",
    val material: String = "",
    val dimensions: String = "",
    val priceInr: Double = 0.0,
    val stockQty: Long = 0L,
    val reorderLevel: Long = 3L,
    val imageKey: String = "img_steel_almirah",
    val description: String = "",
    val isAvailable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isLowStock: Boolean get() = stockQty in 1..reorderLevel
    val isOutOfStock: Boolean get() = stockQty <= 0L || !isAvailable
}

@IgnoreExtraProperties
data class MeasurementRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val phone: String = "",
    val address: String = "",
    val furnitureType: String = "Steel Almirah",
    val roomArea: String = "Master Bedroom",
    val widthFt: Double = 6.0,
    val heightFt: Double = 7.0,
    val depthFt: Double = 2.0,
    val preferredDate: String = "",
    val preferredSlot: String = "10:00 AM - 12:00 PM",
    val notes: String? = null,
    val status: String = "scheduled",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FurnitureOrder(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val userId: String = "",
    val customerName: String = "",
    val phone: String = "",
    val deliveryAddress: String = "",
    val itemSummary: String = "",
    val itemSku: String = "",
    val quantity: Long = 1L,
    val customDimensions: String = "",
    val subtotalInr: Double = 0.0,
    val gstInr: Double = 0.0,
    val totalInr: Double = 0.0,
    val paymentMethod: String = "UPI",
    val paymentStatus: String = "pending",
    val paymentRef: String? = null,
    val trackingStage: String = "Order Confirmed",
    val estimatedDays: Long = 7L,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class AiOrderDraft(
    val furnitureType: String,
    val itemName: String,
    val dimensions: String,
    val quantity: Int,
    val estimatedPriceInr: Double,
    val notes: String
)

enum class TrackingStage(val label: String, val subtitle: String) {
    CONFIRMED("Order Confirmed", "Order locked & workshop job sheet created"),
    MEASUREMENT("Measurement Verified", "Site dimensions & clearances verified"),
    FABRICATION("In Fabrication", "CNC steel bending & PVC lamination in progress"),
    QUALITY_CHECK("Quality Check", "Hinge torque, powder-coat & edge finish check"),
    OUT_FOR_DELIVERY("Out for Delivery", "Dispatched via Aravalli Workshop Logistics"),
    DELIVERED("Delivered", "Installed & handed over with warranty");

    companion object {
        val orderedStages = entries.toList()
        fun indexOfLabel(label: String): Int =
            entries.indexOfFirst { it.label == label }.coerceAtLeast(0)
        fun nextStageLabel(current: String): String? {
            val idx = indexOfLabel(current)
            return entries.getOrNull(idx + 1)?.label
        }
    }
}

object ShopConstants {
    const val SHOP_NAME = "Aravalli Steel PVC Furniture"
    const val SHOP_TAGLINE = "Precision Steel Fabrication & Waterproof PVC Interiors"
    const val SHOP_GSTIN = "08AAACA4921M1Z5"
    const val SHOP_ADDRESS = "Plot 18, RIICO Industrial Area, Aravalli Road, Udaipur 313001"
    const val DEFAULT_WHATSAPP_NUMBER = "919829012345"
    const val DEFAULT_UPI_ID = "aravallifurniture@upi"
    const val ADMIN_EMAIL = "harishpatelh450@gmail.com"
    const val GST_RATE = 0.18

    val CATEGORIES = listOf(
        "All",
        "Steel Almirah",
        "PVC Kitchen",
        "UPVC Doors",
        "Office & Modular",
        "Custom Fabrication"
    )

    @DrawableRes
    fun drawableForKey(imageKey: String): Int = when (imageKey) {
        "img_showroom_hero" -> R.drawable.img_showroom_hero
        "img_steel_almirah" -> R.drawable.img_steel_almirah
        "img_pvc_kitchen" -> R.drawable.img_pvc_kitchen
        "img_pvc_furniture" -> R.drawable.img_pvc_furniture
        else -> R.drawable.img_steel_almirah
    }

    val DEFAULT_CATALOG = listOf(
        InventoryItem(
            id = "arv_stl_01",
            sku = "ARV-STL-01",
            name = "Royal 3-Door Steel Almirah",
            category = "Steel Almirah",
            material = "20-Gauge Tata CRCA Powder-Coated Steel",
            dimensions = "78 x 42 x 22 in",
            priceInr = 24500.0,
            stockQty = 14L,
            reorderLevel = 3L,
            imageKey = "img_steel_almirah",
            description = "Heavy-duty 3-door steel wardrobe with internal safe locker, full-length dressing mirror, and rust-proof epoxy finish."
        ),
        InventoryItem(
            id = "arv_pvc_02",
            sku = "ARV-PVC-02",
            name = "AquaShield PVC Modular Kitchen",
            category = "PVC Kitchen",
            material = "18mm High-Density Virgin PVC Foam Board",
            dimensions = "10 x 8 ft (L-Shape)",
            priceInr = 68000.0,
            stockQty = 6L,
            reorderLevel = 2L,
            imageKey = "img_pvc_kitchen",
            description = "100% waterproof, termite-proof modular kitchen cabinetry with soft-close SS 304 baskets and matte walnut finish."
        ),
        InventoryItem(
            id = "arv_door_03",
            sku = "ARV-DR-03",
            name = "Aravalli WPC & UPVC Designer Door",
            category = "UPVC Doors",
            material = "28mm Solid WPC Waterproof Frame & Shutter",
            dimensions = "81 x 33 x 1.25 in",
            priceInr = 9800.0,
            stockQty = 22L,
            reorderLevel = 5L,
            imageKey = "img_showroom_hero",
            description = "All-weather bathroom and exterior door set resistant to moisture, warping, and termites with CNC fluted grooves."
        ),
        InventoryItem(
            id = "arv_off_04",
            sku = "ARV-OFF-04",
            name = "Executive Steel & PVC Workstation",
            category = "Office & Modular",
            material = "1.2mm Tubular Steel Frame + 18mm PVC Top",
            dimensions = "60 x 30 x 30 in",
            priceInr = 16200.0,
            stockQty = 9L,
            reorderLevel = 3L,
            imageKey = "img_pvc_furniture",
            description = "Industrial-grade office desk with lockable steel pedestal drawer, cable grommets, and scratch-resistant PVC laminate."
        ),
        InventoryItem(
            id = "arv_stl_05",
            sku = "ARV-STL-05",
            name = "Compact Sliding Mirror Almirah",
            category = "Steel Almirah",
            material = "18-Gauge Galvanized Steel + Toughened Glass",
            dimensions = "72 x 36 x 20 in",
            priceInr = 21900.0,
            stockQty = 2L,
            reorderLevel = 3L,
            imageKey = "img_steel_almirah",
            description = "Space-saving sliding-door steel almirah tailored for compact urban bedrooms with anti-jump heavy tracks."
        ),
        InventoryItem(
            id = "arv_pvc_06",
            sku = "ARV-PVC-06",
            name = "Fluted PVC Wardrobe & TV Console",
            category = "PVC Kitchen",
            material = "18mm Waterproof PVC + Brushed Brass Trim",
            dimensions = "84 x 72 x 18 in",
            priceInr = 42500.0,
            stockQty = 4L,
            reorderLevel = 2L,
            imageKey = "img_showroom_hero",
            description = "Contemporary living room & bedroom modular unit combining moisture-proof PVC panels with steel structural framing."
        )
    )
}
