package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.FurnitureOrder
import com.example.data.model.InventoryItem
import com.example.data.model.ShopConstants
import com.example.data.model.UiState
import com.example.data.preferences.ShopSettingsStore
import com.example.data.repository.AravalliRepository
import com.example.firebase.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.components.OrderCheckoutSheet
import com.example.ui.components.PdfBillPreviewDialog
import com.example.ui.screens.BotScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.MeasurementScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ShowroomScreen
import com.example.ui.theme.AravalliTheme
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.AiBotViewModel
import com.example.ui.viewmodel.AravalliViewModel
import com.example.util.BusinessIntegrations
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_SHOWROOM = "showroom"
const val ROUTE_CATALOG = "catalog"
const val ROUTE_MEASURE = "measure"
const val ROUTE_BOT = "bot"
const val ROUTE_ORDERS = "orders"
const val ROUTE_INVENTORY = "inventory"

data class CheckoutDraft(
    val itemName: String,
    val itemSku: String,
    val defaultDimensions: String,
    val unitPriceInr: Double,
    val maxStock: Long,
    val notes: String,
    val inventoryItemId: String?
)

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check initialization with launch intent BEFORE UI or Firebase calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            AravalliTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { }
        )
    } else {
        key(user.uid) {
            MainWorkshopApp(currentUserId = user.uid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWorkshopApp(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val mainViewModel: AravalliViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AravalliViewModel(
                    repository = AravalliRepository(db, Firebase.auth),
                    settingsStore = ShopSettingsStore(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val aiBotViewModel: AiBotViewModel = viewModel(key = "ai_$currentUserId")

    val inventoryState by mainViewModel.inventoryState.collectAsStateWithLifecycle()
    val ordersState by mainViewModel.ordersState.collectAsStateWithLifecycle()
    val measurementsState by mainViewModel.measurementsState.collectAsStateWithLifecycle()
    val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()
    val isAdmin by mainViewModel.isAdmin.collectAsStateWithLifecycle()
    val shopSettings by mainViewModel.shopSettings.collectAsStateWithLifecycle()
    val actionMessage by mainViewModel.actionMessage.collectAsStateWithLifecycle()

    val botMessages by aiBotViewModel.messages.collectAsStateWithLifecycle()
    val botStatus by aiBotViewModel.botStatus.collectAsStateWithLifecycle()
    val draftState by aiBotViewModel.draftState.collectAsStateWithLifecycle()
    val roomAnalysisState by aiBotViewModel.roomAnalysisState.collectAsStateWithLifecycle()

    val catalog = (inventoryState as? UiState.Success<List<InventoryItem>>)?.data
        ?: ShopConstants.DEFAULT_CATALOG
    val orders = (ordersState as? UiState.Success<List<FurnitureOrder>>)?.data ?: emptyList()
    val measurements = (measurementsState as? UiState.Success)?.data ?: emptyList()

    var currentRoute by remember { mutableStateOf(ROUTE_SHOWROOM) }
    var prefilledMeasureType by remember { mutableStateOf<String?>(null) }
    var activeCheckout by remember { mutableStateOf<CheckoutDraft?>(null) }
    var newlyConfirmedBillOrder by remember { mutableStateOf<FurnitureOrder?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearActionMessage()
        }
    }

    if (currentRoute != ROUTE_SHOWROOM) {
        BackHandler { currentRoute = ROUTE_SHOWROOM }
    }

    val bottomDestinations = remember {
        listOf(
            NavDestination(ROUTE_SHOWROOM, "Showroom", Icons.Default.Storefront),
            NavDestination(ROUTE_CATALOG, "Gallery", Icons.Default.PhotoLibrary),
            NavDestination(ROUTE_MEASURE, "Measure", Icons.Default.Straighten),
            NavDestination(ROUTE_BOT, "AI Bot", Icons.Default.SmartToy),
            NavDestination(ROUTE_ORDERS, "Orders", Icons.Default.LocalShipping)
        )
    }

    val railDestinations = remember(bottomDestinations) {
        bottomDestinations + NavDestination(ROUTE_INVENTORY, "Stock", Icons.Default.Inventory2)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                                ROUTE_CATALOG -> "Catalog Gallery"
                                ROUTE_MEASURE -> "Site Measurement"
                                ROUTE_BOT -> "Aravalli Order Bot"
                                ROUTE_ORDERS -> "Orders & PDF Bills"
                                ROUTE_INVENTORY -> "Live Stock Control"
                                else -> "Aravalli Furniture"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { currentRoute = ROUTE_INVENTORY },
                            modifier = Modifier.testTag("top_inventory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Open Inventory Stock Management",
                                tint = if (currentRoute == ROUTE_INVENTORY) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                BusinessIntegrations.openWhatsAppSupport(
                                    context = context,
                                    phoneDigits = shopSettings.whatsappNumber,
                                    message = "Namaste Aravalli Steel PVC Furniture! Connecting from the app for customer support."
                                )
                            },
                            modifier = Modifier.testTag("top_whatsapp_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Live WhatsApp Support",
                                tint = WhatsAppGreen
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        bottomDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { currentRoute = dest.route },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail {
                        railDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { currentRoute = dest.route },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                        when (currentRoute) {
                            ROUTE_SHOWROOM -> ShowroomScreen(
                                userProfile = userProfile,
                                isAdmin = isAdmin,
                                catalog = catalog,
                                orders = orders,
                                measurements = measurements,
                                shopSettings = shopSettings,
                                onNavigateToCatalog = { currentRoute = ROUTE_CATALOG },
                                onNavigateToMeasure = { currentRoute = ROUTE_MEASURE },
                                onNavigateToBot = { currentRoute = ROUTE_BOT },
                                onNavigateToOrders = { currentRoute = ROUTE_ORDERS },
                                onNavigateToInventory = { currentRoute = ROUTE_INVENTORY },
                                onSelectFeaturedItem = { item ->
                                    activeCheckout = CheckoutDraft(
                                        itemName = item.name,
                                        itemSku = item.sku,
                                        defaultDimensions = item.dimensions,
                                        unitPriceInr = item.priceInr,
                                        maxStock = item.stockQty,
                                        notes = item.material,
                                        inventoryItemId = item.id
                                    )
                                },
                                onSaveShopSettings = mainViewModel::updateShopSettings,
                                onSaveCustomerContact = mainViewModel::updateUserContact,
                                onSignOut = {
                                    signOut(credentialManager = credentialManager, scope = scope)
                                }
                            )

                            ROUTE_CATALOG -> CatalogScreen(
                                catalog = catalog,
                                whatsappNumber = shopSettings.whatsappNumber,
                                onOrderProduct = { item ->
                                    activeCheckout = CheckoutDraft(
                                        itemName = item.name,
                                        itemSku = item.sku,
                                        defaultDimensions = item.dimensions,
                                        unitPriceInr = item.priceInr,
                                        maxStock = item.stockQty,
                                        notes = item.material,
                                        inventoryItemId = item.id
                                    )
                                },
                                onRequestMeasurementForProduct = { item ->
                                    prefilledMeasureType = item.category
                                    currentRoute = ROUTE_MEASURE
                                }
                            )

                            ROUTE_MEASURE -> MeasurementScreen(
                                measurements = measurements,
                                isAdmin = isAdmin,
                                initialName = userProfile?.displayName ?: Firebase.auth.currentUser?.displayName.orEmpty(),
                                initialPhone = userProfile?.phone.orEmpty(),
                                initialAddress = userProfile?.address.orEmpty(),
                                prefilledFurnitureType = prefilledMeasureType,
                                roomAnalysisState = roomAnalysisState,
                                whatsappNumber = shopSettings.whatsappNumber,
                                onAnalyzeRoomPhoto = aiBotViewModel::analyzeRoomPhoto,
                                onClearRoomAnalysis = aiBotViewModel::clearRoomAnalysis,
                                onSubmitMeasurement = { name, phone, addr, type, area, w, h, d, date, slot, notes ->
                                    mainViewModel.createMeasurement(
                                        customerName = name,
                                        phone = phone,
                                        address = addr,
                                        furnitureType = type,
                                        roomArea = area,
                                        widthFt = w,
                                        heightFt = h,
                                        depthFt = d,
                                        preferredDate = date,
                                        preferredSlot = slot,
                                        notes = notes
                                    )
                                },
                                onAdvanceStatus = mainViewModel::advanceMeasurementStatus,
                                onConvertToOrder = { m ->
                                    val baseRate = when (m.furnitureType) {
                                        "PVC Kitchen" -> 58000.0
                                        "UPVC Doors" -> 9800.0
                                        "Office & Modular" -> 16200.0
                                        else -> 24000.0
                                    }
                                    activeCheckout = CheckoutDraft(
                                        itemName = "Custom ${m.furnitureType} (${m.roomArea})",
                                        itemSku = "CUSTOM",
                                        defaultDimensions = "${m.widthFt}ft x ${m.heightFt}ft x ${m.depthFt}ft",
                                        unitPriceInr = baseRate,
                                        maxStock = 10L,
                                        notes = m.notes.orEmpty(),
                                        inventoryItemId = null
                                    )
                                }
                            )

                            ROUTE_BOT -> BotScreen(
                                messages = botMessages,
                                botStatus = botStatus,
                                draftState = draftState,
                                catalog = catalog,
                                onSendMessage = { prompt -> aiBotViewModel.sendMessage(prompt, catalog) },
                                onExtractOrderDraft = aiBotViewModel::extractOrderDraftFromChat,
                                onClearDraft = aiBotViewModel::clearDraftState,
                                onPlaceOrderFromDraft = { draft ->
                                    val matchedInv = catalog.firstOrNull {
                                        it.category.equals(draft.furnitureType, ignoreCase = true) && !it.isOutOfStock
                                    }
                                    activeCheckout = CheckoutDraft(
                                        itemName = draft.itemName,
                                        itemSku = matchedInv?.sku ?: "CUSTOM",
                                        defaultDimensions = draft.dimensions,
                                        unitPriceInr = draft.estimatedPriceInr,
                                        maxStock = matchedInv?.stockQty ?: 10L,
                                        notes = draft.notes,
                                        inventoryItemId = matchedInv?.id
                                    )
                                }
                            )

                            ROUTE_ORDERS -> OrdersScreen(
                                orders = orders,
                                isAdmin = isAdmin,
                                upiId = shopSettings.upiId,
                                whatsappNumber = shopSettings.whatsappNumber,
                                onAdvanceStage = mainViewModel::advanceOrderStage,
                                onCompletePayment = mainViewModel::updateOrderPayment
                            )

                            ROUTE_INVENTORY -> InventoryScreen(
                                inventory = catalog,
                                isAdmin = isAdmin,
                                onAdjustStock = mainViewModel::adjustStock,
                                onSaveItem = mainViewModel::saveInventoryItem,
                                onDeleteItem = mainViewModel::deleteInventoryItem
                            )
                        }
                    }
                }
            }
        }
    }

    if (activeCheckout != null) {
        val draft = activeCheckout!!
        OrderCheckoutSheet(
            itemName = draft.itemName,
            itemSku = draft.itemSku,
            defaultDimensions = draft.defaultDimensions,
            unitPriceInr = draft.unitPriceInr,
            maxStock = draft.maxStock,
            initialCustomerName = userProfile?.displayName
                ?: Firebase.auth.currentUser?.displayName.orEmpty(),
            initialPhone = userProfile?.phone.orEmpty(),
            initialAddress = userProfile?.address.orEmpty(),
            initialNotes = draft.notes,
            upiId = shopSettings.upiId,
            inventoryItemId = draft.inventoryItemId,
            onDismiss = { activeCheckout = null },
            onConfirmOrder = { custName, phone, addr, dims, qty, method, status, ref, notes ->
                activeCheckout = null
                mainViewModel.placeOrder(
                    customerName = custName,
                    phone = phone,
                    deliveryAddress = addr,
                    itemSummary = "${draft.itemName} x$qty",
                    itemSku = draft.itemSku,
                    quantity = qty,
                    customDimensions = dims,
                    unitPriceInr = draft.unitPriceInr,
                    paymentMethod = method,
                    paymentStatus = status,
                    paymentRef = ref,
                    notes = notes,
                    inventoryItemId = draft.inventoryItemId,
                    onOrderCreated = { created ->
                        newlyConfirmedBillOrder = created
                        currentRoute = ROUTE_ORDERS
                    }
                )
            }
        )
    }

    if (newlyConfirmedBillOrder != null) {
        PdfBillPreviewDialog(
            order = newlyConfirmedBillOrder!!,
            whatsappNumber = shopSettings.whatsappNumber,
            onDismiss = { newlyConfirmedBillOrder = null }
        )
    }
}
