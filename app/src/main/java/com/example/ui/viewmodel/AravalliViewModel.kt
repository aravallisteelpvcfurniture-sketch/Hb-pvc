package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FurnitureOrder
import com.example.data.model.InventoryItem
import com.example.data.model.MeasurementRequest
import com.example.data.model.ShopConstants
import com.example.data.model.TrackingStage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.preferences.ShopIntegrationSettings
import com.example.data.preferences.ShopSettingsStore
import com.example.data.repository.AravalliRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AravalliViewModel(
    private val repository: AravalliRepository,
    private val settingsStore: ShopSettingsStore,
    private val currentUserId: String
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(repository.isCurrentUserAdmin())
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val shopSettings: StateFlow<ShopIntegrationSettings> = settingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ShopIntegrationSettings()
        )

    val inventoryState: StateFlow<UiState<List<InventoryItem>>> = repository.observeInventory()
        .map<List<InventoryItem>, UiState<List<InventoryItem>>> { items ->
            UiState.Success(items.ifEmpty { ShopConstants.DEFAULT_CATALOG })
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing inventory", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load inventory"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val ordersState: StateFlow<UiState<List<FurnitureOrder>>> = repository
        .observeOrders(currentUserId, asAdmin = repository.isCurrentUserAdmin())
        .map<List<FurnitureOrder>, UiState<List<FurnitureOrder>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing orders", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe orders"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val measurementsState: StateFlow<UiState<List<MeasurementRequest>>> = repository
        .observeMeasurements(currentUserId, asAdmin = repository.isCurrentUserAdmin())
        .map<List<MeasurementRequest>, UiState<List<MeasurementRequest>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing measurements", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe measurements"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        bootstrapSession()
    }

    private fun bootstrapSession() {
        viewModelScope.launch {
            val firebaseUser = Firebase.auth.currentUser
            val name = firebaseUser?.displayName.orEmpty()
            val email = firebaseUser?.email.orEmpty()
            repository.ensureUserProfile(name, email)
            repository.getUserProfile(currentUserId).onSuccess {
                _userProfile.value = it
            }
            val adminDoc = repository.checkAdminDocument(currentUserId)
            val isShopAdmin = repository.isCurrentUserAdmin() || adminDoc
            _isAdmin.value = isShopAdmin
            if (isShopAdmin) {
                repository.seedDefaultInventoryIfEmpty()
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setActionMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun updateUserContact(displayName: String, phone: String, address: String) {
        viewModelScope.launch {
            repository.updateUserContact(displayName, phone, address)
                .onSuccess {
                    repository.getUserProfile(currentUserId).onSuccess { _userProfile.value = it }
                    _actionMessage.value = "Contact details saved"
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update contact" }
        }
    }

    fun updateShopSettings(whatsappNumber: String, upiId: String) {
        viewModelScope.launch {
            settingsStore.updateSettings(whatsappNumber, upiId)
            _actionMessage.value = "Workshop integration settings saved"
        }
    }

    fun placeOrder(
        customerName: String,
        phone: String,
        deliveryAddress: String,
        itemSummary: String,
        itemSku: String,
        quantity: Long,
        customDimensions: String,
        unitPriceInr: Double,
        paymentMethod: String,
        paymentStatus: String,
        paymentRef: String?,
        notes: String?,
        inventoryItemId: String? = null,
        onOrderCreated: (FurnitureOrder) -> Unit = {}
    ) {
        viewModelScope.launch {
            val subtotal = unitPriceInr * quantity
            val gst = subtotal * ShopConstants.GST_RATE
            val total = subtotal + gst
            val orderNo = "ARV-${(1000..9999).random()}"

            val order = FurnitureOrder(
                orderNumber = orderNo,
                userId = currentUserId,
                customerName = customerName,
                phone = phone,
                deliveryAddress = deliveryAddress,
                itemSummary = itemSummary,
                itemSku = itemSku,
                quantity = quantity,
                customDimensions = customDimensions,
                subtotalInr = subtotal,
                gstInr = gst,
                totalInr = total,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus,
                paymentRef = paymentRef,
                trackingStage = "Order Confirmed",
                estimatedDays = if (itemSku == "CUSTOM") 10L else 5L,
                notes = notes
            )

            repository.placeOrder(order, inventoryItemId, quantity)
                .onSuccess { newId ->
                    _actionMessage.value = "Order $orderNo confirmed!"
                    onOrderCreated(order.copy(id = newId))
                }
                .onFailure { e ->
                    _actionMessage.value = e.localizedMessage ?: "Failed to place order"
                }
        }
    }

    fun updateOrderPayment(
        orderId: String,
        paymentMethod: String,
        paymentStatus: String,
        paymentRef: String
    ) {
        viewModelScope.launch {
            repository.updateOrderPayment(orderId, paymentMethod, paymentStatus, paymentRef)
                .onSuccess { _actionMessage.value = "Payment recorded successfully" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Payment update failed" }
        }
    }

    fun advanceOrderStage(order: FurnitureOrder) {
        val next = TrackingStage.nextStageLabel(order.trackingStage) ?: return
        viewModelScope.launch {
            repository.advanceOrderTracking(order.id, next)
                .onSuccess { _actionMessage.value = "Order ${order.orderNumber} moved to $next" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Unable to advance stage" }
        }
    }

    fun createMeasurement(
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
        notes: String?,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val request = MeasurementRequest(
                userId = currentUserId,
                customerName = customerName,
                phone = phone,
                address = address,
                furnitureType = furnitureType,
                roomArea = roomArea,
                widthFt = widthFt,
                heightFt = heightFt,
                depthFt = depthFt,
                preferredDate = preferredDate,
                preferredSlot = preferredSlot,
                notes = notes
            )
            repository.createMeasurement(request)
                .onSuccess {
                    _actionMessage.value = "Site measurement visit scheduled!"
                    onCreated()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to schedule visit" }
        }
    }

    fun advanceMeasurementStatus(measurement: MeasurementRequest) {
        val next = when (measurement.status) {
            "scheduled" -> "measured"
            "measured" -> "quoted"
            "quoted" -> "converted"
            else -> return
        }
        viewModelScope.launch {
            repository.updateMeasurementStatus(measurement.id, next)
                .onSuccess { _actionMessage.value = "Measurement marked as $next" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun adjustStock(item: InventoryItem, delta: Long) {
        viewModelScope.launch {
            val newQty = (item.stockQty + delta).coerceAtLeast(0L)
            repository.adjustInventoryStock(item.id, newQty)
                .onSuccess { _actionMessage.value = "${item.sku} stock updated to $newQty" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Stock update requires admin access" }
        }
    }

    fun saveInventoryItem(item: InventoryItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.upsertInventoryItem(item)
                .onSuccess {
                    _actionMessage.value = "${item.name} saved to inventory"
                    onDone()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Inventory save requires admin access" }
        }
    }

    fun deleteInventoryItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteInventoryItem(itemId)
                .onSuccess { _actionMessage.value = "Item removed from inventory" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Delete requires admin access" }
        }
    }

    private companion object {
        const val TAG = "AravalliVM"
    }
}
