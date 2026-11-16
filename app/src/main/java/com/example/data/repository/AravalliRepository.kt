package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FurnitureOrder
import com.example.data.model.InventoryItem
import com.example.data.model.MeasurementRequest
import com.example.data.model.ShopConstants
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AravalliRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(ShopConstants.ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    suspend fun checkAdminDocument(userId: String): Boolean {
        return try {
            val doc = db.collection("admins").document(userId).get().await()
            doc.exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureUserProfile(displayName: String, email: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.ifBlank { "Valued Customer" }.take(80)
                val safeEmail = email.ifBlank { "customer@aravallifurniture.com" }.take(120)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "role" to "customer",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> = runCatching {
        val docRef = db.collection("users").document(userId)
        try {
            docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateUserContact(displayName: String, phone: String, address: String): Result<Unit> =
        runCatching {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            val updates = mapOf(
                "displayName" to displayName.trim().ifBlank { "Valued Customer" }.take(80),
                "phone" to phone.trim().take(20),
                "address" to address.trim().take(250),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }

    // --- INVENTORY ---

    fun observeInventory(): Flow<List<InventoryItem>> = flow {
        requireUserId()
        val path = "inventory"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InventoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sku }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun seedDefaultInventoryIfEmpty(): Result<Unit> = runCatching {
        requireUserId()
        val collectionRef = db.collection("inventory")
        try {
            val existing = collectionRef.limit(1).get().await()
            if (existing.isEmpty) {
                val batch = db.batch()
                ShopConstants.DEFAULT_CATALOG.forEach { item ->
                    val docRef = collectionRef.document(item.id)
                    val payload = mapOf(
                        "sku" to item.sku,
                        "name" to item.name,
                        "category" to item.category,
                        "material" to item.material,
                        "dimensions" to item.dimensions,
                        "priceInr" to item.priceInr,
                        "stockQty" to item.stockQty,
                        "reorderLevel" to item.reorderLevel,
                        "imageKey" to item.imageKey,
                        "description" to item.description,
                        "isAvailable" to item.isAvailable,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "inventory")
            throw e
        }
    }

    suspend fun upsertInventoryItem(item: InventoryItem): Result<String> = runCatching {
        requireUserId()
        val docId = item.id.ifBlank { "inv_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val docRef = db.collection("inventory").document(docId)
        try {
            if (item.id.isBlank() || item.createdAt == null) {
                val payload = mapOf(
                    "sku" to item.sku.trim().take(40),
                    "name" to item.name.trim().take(120),
                    "category" to item.category,
                    "material" to item.material.trim().take(120),
                    "dimensions" to item.dimensions.trim().take(80),
                    "priceInr" to item.priceInr,
                    "stockQty" to item.stockQty,
                    "reorderLevel" to item.reorderLevel,
                    "imageKey" to item.imageKey,
                    "description" to item.description.trim().take(500),
                    "isAvailable" to (item.stockQty > 0L && item.isAvailable),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "sku" to item.sku.trim().take(40),
                    "name" to item.name.trim().take(120),
                    "category" to item.category,
                    "material" to item.material.trim().take(120),
                    "dimensions" to item.dimensions.trim().take(80),
                    "priceInr" to item.priceInr,
                    "stockQty" to item.stockQty,
                    "reorderLevel" to item.reorderLevel,
                    "imageKey" to item.imageKey,
                    "description" to item.description.trim().take(500),
                    "isAvailable" to (item.stockQty > 0L && item.isAvailable),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun adjustInventoryStock(itemId: String, newStockQty: Long): Result<Unit> = runCatching {
        requireUserId()
        val safeQty = newStockQty.coerceAtLeast(0L)
        val docRef = db.collection("inventory").document(itemId)
        try {
            docRef.update(
                mapOf(
                    "stockQty" to safeQty,
                    "isAvailable" to (safeQty > 0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteInventoryItem(itemId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("inventory").document(itemId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- VISITOR MEASUREMENTS ---

    fun observeMeasurements(userId: String, asAdmin: Boolean = false): Flow<List<MeasurementRequest>> = flow {
        val uid = requireUserId()
        val path = "measurements"
        val query = if (asAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("userId", userId.ifBlank { uid })
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MeasurementRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createMeasurement(request: MeasurementRequest): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = "meas_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("measurements").document(docId)
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("customerName", request.customerName.trim().take(80))
            put("phone", request.phone.trim().take(20))
            put("address", request.address.trim().take(250))
            put("furnitureType", request.furnitureType.trim().take(80))
            put("roomArea", request.roomArea.trim().take(80))
            put("widthFt", request.widthFt)
            put("heightFt", request.heightFt)
            put("depthFt", request.depthFt)
            put("preferredDate", request.preferredDate.trim().take(40))
            put("preferredSlot", request.preferredSlot.trim().take(40))
            if (!request.notes.isNullOrBlank()) {
                put("notes", request.notes.trim().take(500))
            }
            put("status", "scheduled")
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateMeasurementStatus(measurementId: String, status: String): Result<Unit> =
        runCatching {
            requireUserId()
            val docRef = db.collection("measurements").document(measurementId)
            try {
                docRef.update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }

    // --- ORDERS ---

    fun observeOrders(userId: String, asAdmin: Boolean = false): Flow<List<FurnitureOrder>> = flow {
        val uid = requireUserId()
        val path = "orders"
        val query = if (asAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("userId", userId.ifBlank { uid })
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FurnitureOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun getOrderById(orderId: String): Result<FurnitureOrder?> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        try {
            docRef.get().await()
                .toObject(FurnitureOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun placeOrder(
        order: FurnitureOrder,
        inventoryItemId: String? = null,
        decrementQty: Long = 1L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = "ord_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val orderRef = db.collection("orders").document(docId)
        val payload = buildMap<String, Any> {
            put("orderNumber", order.orderNumber.take(40))
            put("userId", uid)
            put("customerName", order.customerName.trim().take(80))
            put("phone", order.phone.trim().take(20))
            put("deliveryAddress", order.deliveryAddress.trim().take(250))
            put("itemSummary", order.itemSummary.trim().take(300))
            put("itemSku", order.itemSku.trim().take(40))
            put("quantity", order.quantity.coerceIn(1L, 100L))
            put("customDimensions", order.customDimensions.trim().take(100))
            put("subtotalInr", order.subtotalInr)
            put("gstInr", order.gstInr)
            put("totalInr", order.totalInr)
            put("paymentMethod", order.paymentMethod)
            put("paymentStatus", order.paymentStatus)
            if (!order.paymentRef.isNullOrBlank()) {
                put("paymentRef", order.paymentRef.trim().take(80))
            }
            put("trackingStage", "Order Confirmed")
            put("estimatedDays", order.estimatedDays.coerceIn(1L, 90L))
            if (!order.notes.isNullOrBlank()) {
                put("notes", order.notes.trim().take(500))
            }
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            if (!inventoryItemId.isNullOrBlank()) {
                val invRef = db.collection("inventory").document(inventoryItemId)
                db.runTransaction { transaction ->
                    val invSnap = transaction.get(invRef)
                    if (invSnap.exists()) {
                        val currentStock = invSnap.getLong("stockQty") ?: 0L
                        if (currentStock >= decrementQty && decrementQty > 0L) {
                            val updatedStock = currentStock - decrementQty
                            transaction.update(
                                invRef,
                                mapOf(
                                    "stockQty" to updatedStock,
                                    "isAvailable" to (updatedStock > 0L),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    transaction.set(orderRef, payload)
                }.await()
            } else {
                orderRef.set(payload).await()
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, orderRef.path)
            throw e
        }
    }

    suspend fun updateOrderPayment(
        orderId: String,
        paymentMethod: String,
        paymentStatus: String,
        paymentRef: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        val updates = buildMap<String, Any> {
            put("paymentMethod", paymentMethod)
            put("paymentStatus", paymentStatus)
            if (paymentRef.isNotBlank()) {
                put("paymentRef", paymentRef.trim().take(80))
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun advanceOrderTracking(orderId: String, nextStage: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("orders").document(orderId)
        try {
            docRef.update(
                mapOf(
                    "trackingStage" to nextStage,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String? = auth.currentUser?.uid
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: return
        if (expectedUid == null || auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
