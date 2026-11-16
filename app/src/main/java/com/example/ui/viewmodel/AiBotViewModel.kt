package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiOrderDraft
import com.example.data.model.ChatMessage
import com.example.data.model.InventoryItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

sealed interface AiBotStatus {
    data object Idle : AiBotStatus
    data object Loading : AiBotStatus
    data class Streaming(val partialText: String) : AiBotStatus
    data class Error(val message: String) : AiBotStatus
}

sealed interface AiDraftState {
    data object Idle : AiDraftState
    data object Extracting : AiDraftState
    data class Ready(val draft: AiOrderDraft) : AiDraftState
    data class Error(val message: String) : AiDraftState
}

sealed interface RoomAnalysisState {
    data object Idle : RoomAnalysisState
    data object Analyzing : RoomAnalysisState
    data class Success(val recommendation: String) : RoomAnalysisState
    data class Error(val message: String) : RoomAnalysisState
}

class AiBotViewModel : ViewModel() {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are the official Sales & Order Assistant Bot for "Aravalli Steel PVC Furniture" (Aravalli Workshop).
                    You help customers in friendly English, Hindi, or Hinglish:
                    - Recommend Tata CRCA powder-coated Steel Almirahs, 18mm Waterproof Virgin PVC Modular Kitchens, UPVC/WPC Designer Doors, and Office Furniture.
                    - Estimate pricing in INR (steel almirahs ₹18,000-₹35,000, PVC modular kitchens ₹55,000-₹1,10,000, WPC doors ₹8,500-₹15,000, custom fabrication ₹1,400/sq.ft PVC or ₹2,200/sq.ft Steel), plus 18% GST.
                    - Guide customers on dimensions (width x height x depth in feet/inches), site measurement booking, UPI payment, and PDF invoice generation.
                    Keep answers concise, practical, and helpful so customers can place orders easily.
                    """.trimIndent()
                )
            }
        )
    }

    private val draftSchema = Schema.obj(
        properties = mapOf(
            "furnitureType" to Schema.enumeration(
                values = listOf(
                    "Steel Almirah",
                    "PVC Kitchen",
                    "UPVC Doors",
                    "Office & Modular",
                    "Custom Fabrication"
                ),
                description = "Matched Aravalli furniture category"
            ),
            "itemName" to Schema.string(description = "Short product or custom build title"),
            "dimensions" to Schema.string(description = "Dimensions e.g. 7ft x 4ft x 22in"),
            "quantity" to Schema.integer(description = "Requested quantity, minimum 1"),
            "estimatedPriceInr" to Schema.double(description = "Estimated unit price in INR before GST"),
            "notes" to Schema.string(description = "Finish, color, or custom specification notes")
        )
    )

    private val draftModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = draftSchema
            }
        )
    }

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "welcome",
                isUser = false,
                text = "Namaste! Welcome to Aravalli Steel PVC Furniture.\n\nTell me what furniture you need (Steel Almirah, PVC Modular Kitchen, UPVC Doors, or custom wall measurements) in English or Hinglish, and I'll help you quote & place your order!"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _botStatus = MutableStateFlow<AiBotStatus>(AiBotStatus.Idle)
    val botStatus: StateFlow<AiBotStatus> = _botStatus.asStateFlow()

    private val _draftState = MutableStateFlow<AiDraftState>(AiDraftState.Idle)
    val draftState: StateFlow<AiDraftState> = _draftState.asStateFlow()

    private val _roomAnalysisState = MutableStateFlow<RoomAnalysisState>(RoomAnalysisState.Idle)
    val roomAnalysisState: StateFlow<RoomAnalysisState> = _roomAnalysisState.asStateFlow()

    fun sendMessage(userInput: String, catalog: List<InventoryItem>) {
        val trimmed = userInput.trim()
        if (trimmed.isBlank()) return

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = trimmed
        )
        _messages.value = _messages.value + userMsg
        _botStatus.value = AiBotStatus.Loading

        val catalogContext = catalog.joinToString("\n") { item ->
            "- ${item.sku}: ${item.name} (${item.category}), ${item.dimensions}, Rs.${item.priceInr.toInt()}, Stock: ${item.stockQty}"
        }

        val conversationHistory = _messages.value.takeLast(8).joinToString("\n") { msg ->
            if (msg.isUser) "Customer: ${msg.text}" else "Bot: ${msg.text}"
        }

        val prompt = """
            Live Catalog & Stock:
            $catalogContext

            Recent Chat:
            $conversationHistory

            Reply to the customer's latest message clearly with relevant products, dimensions, and next step (Place Order or Book Free Measurement).
        """.trimIndent()

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    chatModel.generateContentStream(prompt).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        accumulated.append(part)
                        _botStatus.value = AiBotStatus.Streaming(accumulated.toString())
                    }
                }
                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotBlank()) {
                    _messages.value = _messages.value + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = finalReply
                    )
                    _botStatus.value = AiBotStatus.Idle
                } else {
                    _botStatus.value = AiBotStatus.Error("Assistant returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat generation timed out", e)
                _botStatus.value = AiBotStatus.Error("Request timed out after 60s. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                _botStatus.value = AiBotStatus.Error(e.localizedMessage ?: "Failed to contact AI assistant.")
            }
        }
    }

    fun extractOrderDraftFromChat() {
        val transcript = _messages.value.joinToString("\n") { msg ->
            if (msg.isUser) "Customer: ${msg.text}" else "Bot: ${msg.text}"
        }
        _draftState.value = AiDraftState.Extracting
        viewModelScope.launch {
            try {
                val response = withTimeout(60_000L) {
                    draftModel.generateContent(
                        """
                        Extract the customer's desired Steel or PVC furniture order details from this conversation into JSON.
                        If exact dimensions aren't mentioned, suggest standard dimensions for that furniture type.
                        Conversation:
                        $transcript
                        """.trimIndent()
                    )
                }
                val jsonText = response.text ?: throw IllegalStateException("Empty structured response")
                val json = JSONObject(jsonText)
                val draft = AiOrderDraft(
                    furnitureType = json.optString("furnitureType", "Steel Almirah"),
                    itemName = json.optString("itemName", "Custom Steel PVC Unit"),
                    dimensions = json.optString("dimensions", "78 x 42 x 22 in"),
                    quantity = json.optInt("quantity", 1).coerceAtLeast(1),
                    estimatedPriceInr = json.optDouble("estimatedPriceInr", 24000.0).coerceAtLeast(1000.0),
                    notes = json.optString("notes", "Standard powder-coated / waterproof finish")
                )
                _draftState.value = AiDraftState.Ready(draft)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Draft extraction timed out", e)
                _draftState.value = AiDraftState.Error("Draft extraction timed out. Try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Draft extraction failed", e)
                _draftState.value = AiDraftState.Error(e.localizedMessage ?: "Could not extract order draft.")
            }
        }
    }

    fun clearDraftState() {
        _draftState.value = AiDraftState.Idle
    }

    fun analyzeRoomPhoto(bitmap: Bitmap, furnitureType: String) {
        _roomAnalysisState.value = RoomAnalysisState.Analyzing
        viewModelScope.launch {
            try {
                val response = withTimeout(60_000L) {
                    chatModel.generateContent(
                        content {
                            image(bitmap)
                            text(
                                "Analyze this room/wall space for installing a $furnitureType by Aravalli Steel PVC Furniture. " +
                                    "Suggest ideal width/height clearance, moisture-proof PVC vs heavy-duty Steel recommendations, and finish tips in 4 concise bullet points."
                            )
                        }
                    )
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    _roomAnalysisState.value = RoomAnalysisState.Success(text)
                } else {
                    _roomAnalysisState.value = RoomAnalysisState.Error("Unable to analyze photo.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Room photo analysis timed out", e)
                _roomAnalysisState.value = RoomAnalysisState.Error("Photo analysis timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Room photo analysis failed", e)
                _roomAnalysisState.value = RoomAnalysisState.Error(e.localizedMessage ?: "Photo analysis failed.")
            }
        }
    }

    fun clearRoomAnalysis() {
        _roomAnalysisState.value = RoomAnalysisState.Idle
    }

    private companion object {
        const val TAG = "AiBotVM"
    }
}
