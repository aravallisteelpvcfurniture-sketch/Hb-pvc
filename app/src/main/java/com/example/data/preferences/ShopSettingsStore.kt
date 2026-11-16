package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ShopConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shopSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "aravalli_shop_settings")

data class ShopIntegrationSettings(
    val whatsappNumber: String = ShopConstants.DEFAULT_WHATSAPP_NUMBER,
    val upiId: String = ShopConstants.DEFAULT_UPI_ID
)

class ShopSettingsStore(private val context: Context) {
    private val whatsappKey = stringPreferencesKey("whatsapp_number")
    private val upiIdKey = stringPreferencesKey("upi_id")

    val settings: Flow<ShopIntegrationSettings> = context.shopSettingsDataStore.data.map { prefs ->
        ShopIntegrationSettings(
            whatsappNumber = prefs[whatsappKey]?.ifBlank { null } ?: ShopConstants.DEFAULT_WHATSAPP_NUMBER,
            upiId = prefs[upiIdKey]?.ifBlank { null } ?: ShopConstants.DEFAULT_UPI_ID
        )
    }

    suspend fun updateSettings(whatsappNumber: String, upiId: String) {
        context.shopSettingsDataStore.edit { prefs ->
            prefs[whatsappKey] = whatsappNumber.filter { it.isDigit() }
                .ifBlank { ShopConstants.DEFAULT_WHATSAPP_NUMBER }
            prefs[upiIdKey] = upiId.trim().ifBlank { ShopConstants.DEFAULT_UPI_ID }
        }
    }
}
