package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FurnitureOrder
import com.example.data.model.MeasurementRequest
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AravalliRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun placeOrder_authenticatedCustomer_createsAndObservesOwnOrder() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = AravalliRepository(firestore, auth)

        val order = FurnitureOrder(
            orderNumber = "ARV-2026-2001",
            customerName = "Alice Sharma",
            phone = "9876543210",
            deliveryAddress = "14 Aravalli Road, Udaipur",
            itemSummary = "Royal 3-Door Steel Almirah x1",
            itemSku = "ARV-STL-01",
            quantity = 1L,
            customDimensions = "78 x 42 x 22 in",
            subtotalInr = 24500.0,
            gstInr = 4410.0,
            totalInr = 28910.0,
            paymentMethod = "UPI",
            paymentStatus = "pending",
            estimatedDays = 7L
        )

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.placeOrder(order).getOrThrow()
        }
        assertTrue(orderId.isNotBlank())

        val orders = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeOrders(aliceUid).first { list -> list.any { it.id == orderId } }
        }
        assertTrue(orders.any { it.id == orderId && it.orderNumber == "ARV-2026-2001" })
    }

    @Test
    fun getOrderById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = AravalliRepository(firestore, auth)
        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.placeOrder(
                FurnitureOrder(
                    orderNumber = "ARV-2026-2002",
                    customerName = "Alice",
                    phone = "9876543210",
                    deliveryAddress = "Sector 4",
                    itemSummary = "PVC Kitchen x1",
                    itemSku = "ARV-PVC-02",
                    quantity = 1L,
                    customDimensions = "10 x 8 ft",
                    subtotalInr = 68000.0,
                    gstInr = 12240.0,
                    totalInr = 80240.0,
                    paymentMethod = "UPI",
                    paymentStatus = "pending",
                    estimatedDays = 10L
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = AravalliRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getOrderById(orderId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun createAndObserveMeasurement_authenticatedCustomer_succeedsAndBlocksCrossUser() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AravalliRepository(firestore, auth)

        val measurementId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createMeasurement(
                MeasurementRequest(
                    customerName = "Alice",
                    phone = "9876543210",
                    address = "22 Lake City",
                    furnitureType = "PVC Kitchen",
                    roomArea = "Kitchen",
                    widthFt = 10.0,
                    heightFt = 8.0,
                    depthFt = 2.0,
                    preferredDate = "2026-11-22",
                    preferredSlot = "10:00 AM - 12:00 PM",
                    notes = "Matte walnut finish"
                )
            ).getOrThrow()
        }

        val list = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMeasurements(aliceUid).first { items -> items.any { it.id == measurementId } }
        }
        assertTrue(list.any { it.id == measurementId })
    }

    @Test
    fun observeOrders_unauthenticatedCaller_failsWithIllegalStateOrPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = AravalliRepository(firestore, auth)
        val failed = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeOrders("unknown_uid").first()
            }
            false
        } catch (_: Exception) {
            true
        }
        assertTrue(failed)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_aravalli@test.com"
        const val BOB_EMAIL = "bob_aravalli@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
