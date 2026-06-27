package com.example.drivingassistantapp.data

import android.app.Notification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

// Model for unread messages
data class WhatsAppMessage(
    val sender: String,
    val text: String,
    val replyAction: Notification.Action? = null
)

// States for the Assistant
enum class AssistantState {
    IDLE,
    SPEAKING,
    LISTENING_REPLY,
    LISTENING_COMMAND
}

interface DataRepository {
    val data: Flow<List<String>>
    val assistantState: StateFlow<AssistantState>
    val lastMessage: StateFlow<WhatsAppMessage?>
    val lastLogs: StateFlow<List<String>>
    val voiceCommandRequest: Flow<Unit>
    val checkUnreadRequest: Flow<Unit>
    val assistantFeedback: Flow<String>

    fun setAssistantState(state: AssistantState)
    fun setLastMessage(message: WhatsAppMessage?)
    fun addLog(log: String)
    fun triggerVoiceCommandRequest()
    fun triggerCheckUnreadRequest()
    fun triggerAssistantFeedback(text: String)
}

class DefaultDataRepository : DataRepository {
    
    override val data: Flow<List<String>> = flow { emit(listOf("Driving Assistant Active")) }

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    override val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _lastMessage = MutableStateFlow<WhatsAppMessage?>(null)
    override val lastMessage: StateFlow<WhatsAppMessage?> = _lastMessage.asStateFlow()

    private val _lastLogs = MutableStateFlow<List<String>>(listOf("Aplikasi dimulai. Berjalan di latar belakang."))
    override val lastLogs: StateFlow<List<String>> = _lastLogs.asStateFlow()

    private val _voiceCommandRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val voiceCommandRequest: Flow<Unit> = _voiceCommandRequest.asSharedFlow()

    private val _checkUnreadRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val checkUnreadRequest: Flow<Unit> = _checkUnreadRequest.asSharedFlow()

    private val _assistantFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val assistantFeedback: Flow<String> = _assistantFeedback.asSharedFlow()

    override fun setAssistantState(state: AssistantState) {
        _assistantState.value = state
        addLog("Status asisten: $state")
    }

    override fun setLastMessage(message: WhatsAppMessage?) {
        _lastMessage.value = message
        if (message != null) {
            addLog("Pesan masuk dari ${message.sender}: \"${message.text}\"")
        }
    }

    override fun addLog(log: String) {
        val currentLogs = _lastLogs.value.toMutableList()
        currentLogs.add(0, log) // Add to top
        if (currentLogs.size > 30) {
            currentLogs.removeLast()
        }
        _lastLogs.value = currentLogs
    }

    override fun triggerVoiceCommandRequest() {
        _voiceCommandRequest.tryEmit(Unit)
    }

    override fun triggerCheckUnreadRequest() {
        _checkUnreadRequest.tryEmit(Unit)
    }

    override fun triggerAssistantFeedback(text: String) {
        _assistantFeedback.tryEmit(text)
    }

    companion object {
        @Volatile
        private var instance: DefaultDataRepository? = null

        fun getInstance(): DefaultDataRepository {
            return instance ?: synchronized(this) {
                instance ?: DefaultDataRepository().also { instance = it }
            }
        }
    }
}
