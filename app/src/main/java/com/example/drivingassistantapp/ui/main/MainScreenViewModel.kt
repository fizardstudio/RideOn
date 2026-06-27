package com.example.drivingassistantapp.ui.main

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivingassistantapp.data.AssistantState
import com.example.drivingassistantapp.data.DataRepository
import com.example.drivingassistantapp.data.WhatsAppMessage
import com.example.drivingassistantapp.service.AssistantService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val assistantState: AssistantState = AssistantState.IDLE,
    val lastMessage: WhatsAppMessage? = null,
    val logs: List<String> = emptyList()
)

class MainScreenViewModel(private val repository: DataRepository) : ViewModel() {

    val uiState: StateFlow<MainUiState> = combine(
        repository.assistantState,
        repository.lastMessage,
        repository.lastLogs
    ) { state, msg, logs ->
        MainUiState(state, msg, logs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun onMicClicked() {
        repository.triggerVoiceCommandRequest()
    }

    fun startService(context: Context) {
        val intent = Intent(context, AssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        repository.addLog("Meminta sistem memulai Foreground Service...")
    }

    fun stopService(context: Context) {
        val intent = Intent(context, AssistantService::class.java)
        context.stopService(intent)
        repository.setAssistantState(AssistantState.IDLE)
        repository.addLog("Foreground Service dihentikan.")
    }
}
