package com.example.drivingassistantapp.data

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

// Model for WhatsApp messages
data class WhatsAppMessage(
    val sender: String,
    val text: String,
    val replyAction: Notification.Action? = null,
    val isVoiceNote: Boolean = false
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
    val checkSenderRequest: Flow<String>
    val assistantFeedback: Flow<String>
    
    // Auto-Read Preferences
    val autoReadEnabled: StateFlow<Boolean>
    fun setAutoReadEnabled(enabled: Boolean)

    // Driving Mode (Auto-Reply)
    val drivingModeEnabled: StateFlow<Boolean>
    fun setDrivingModeEnabled(enabled: Boolean)

    // Ignore Groups
    val ignoreGroupsEnabled: StateFlow<Boolean>
    fun setIgnoreGroupsEnabled(enabled: Boolean)

    // Auto-Reply Template Text
    val autoReplyTemplate: StateFlow<String>
    fun setAutoReplyTemplate(template: String)

    // Favorite Contacts mapping (Lowercase Name -> Phone Number)
    val favoriteContacts: StateFlow<Map<String, String>>
    fun addFavoriteContact(name: String, phoneNumber: String)
    fun removeFavoriteContact(name: String)
    fun getPhoneNumberForName(name: String): String?

    // Unread messages queue for sequential reading
    val unreadQueue: StateFlow<List<WhatsAppMessage>>
    fun setUnreadQueue(queue: List<WhatsAppMessage>)
    fun popUnreadQueue(): WhatsAppMessage?

    // WhatsApp Auto-Send flag
    val autoSendPending: StateFlow<Boolean>
    fun setAutoSendPending(pending: Boolean)

    // Speech Rate control (Speed)
    val speechRate: StateFlow<Float>
    fun setSpeechRate(rate: Float)
    
    fun setAssistantState(state: AssistantState)
    fun setLastMessage(message: WhatsAppMessage?)
    fun addLog(log: String)
    fun triggerVoiceCommandRequest()
    fun triggerCheckUnreadRequest()
    fun triggerCheckSenderRequest(name: String)
    fun triggerAssistantFeedback(text: String)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("ride_on_prefs", Context.MODE_PRIVATE)

    override val data: Flow<List<String>> = flow { emit(listOf("Ride On Active")) }

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

    private val _checkSenderRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val checkSenderRequest: Flow<String> = _checkSenderRequest.asSharedFlow()

    private val _assistantFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val assistantFeedback: Flow<String> = _assistantFeedback.asSharedFlow()

    // Preferences
    private val _autoReadEnabled = MutableStateFlow(prefs.getBoolean("auto_read_enabled", true))
    override val autoReadEnabled: StateFlow<Boolean> = _autoReadEnabled.asStateFlow()

    private val _drivingModeEnabled = MutableStateFlow(prefs.getBoolean("driving_mode_enabled", false))
    override val drivingModeEnabled: StateFlow<Boolean> = _drivingModeEnabled.asStateFlow()

    private val _ignoreGroupsEnabled = MutableStateFlow(prefs.getBoolean("ignore_groups_enabled", false))
    override val ignoreGroupsEnabled: StateFlow<Boolean> = _ignoreGroupsEnabled.asStateFlow()

    private val _autoReplyTemplate = MutableStateFlow(
        prefs.getString(
            "auto_reply_template",
            "Halo, saya sedang menyetir menggunakan Ride On Asisten. Saya akan membalas pesan Anda nanti."
        ) ?: "Halo, saya sedang menyetir menggunakan Ride On Asisten. Saya akan membalas pesan Anda nanti."
    )
    override val autoReplyTemplate: StateFlow<String> = _autoReplyTemplate.asStateFlow()

    // Favorite Contacts mapping
    private val _favoriteContacts = MutableStateFlow<Map<String, String>>(emptyMap())
    override val favoriteContacts: StateFlow<Map<String, String>> = _favoriteContacts.asStateFlow()

    // Unread messages queue
    private val _unreadQueue = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    override val unreadQueue: StateFlow<List<WhatsAppMessage>> = _unreadQueue.asStateFlow()

    private val _autoSendPending = MutableStateFlow(false)
    override val autoSendPending: StateFlow<Boolean> = _autoSendPending.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 1.0f))
    override val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    init {
        _favoriteContacts.value = loadFavoriteContactsFromPrefs()
    }

    override fun setAutoReadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_read_enabled", enabled).apply()
        _autoReadEnabled.value = enabled
        addLog("Auto-Read diubah menjadi: ${if (enabled) "Aktif" else "Nonaktif"}")
    }

    override fun setDrivingModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("driving_mode_enabled", enabled).apply()
        _drivingModeEnabled.value = enabled
        addLog("Mode Menyetir (Auto-Reply) diubah menjadi: ${if (enabled) "Aktif" else "Nonaktif"}")
    }

    override fun setIgnoreGroupsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ignore_groups_enabled", enabled).apply()
        _ignoreGroupsEnabled.value = enabled
        addLog("Abaikan Chat Grup diubah menjadi: ${if (enabled) "Aktif" else "Nonaktif"}")
    }

    override fun setAutoReplyTemplate(template: String) {
        prefs.edit().putString("auto_reply_template", template).apply()
        _autoReplyTemplate.value = template
        addLog("Template Auto-Reply diperbarui.")
    }

    override fun setAutoSendPending(pending: Boolean) {
        _autoSendPending.value = pending
    }

    override fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat("speech_rate", rate).apply()
        _speechRate.value = rate
        addLog("Kecepatan suara diubah menjadi: ${rate}x")
    }

    override fun addFavoriteContact(name: String, phoneNumber: String) {
        val cleanName = name.lowercase().trim()
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        val finalNumber = if (cleanNumber.startsWith("0")) {
            "62" + cleanNumber.substring(1)
        } else {
            cleanNumber
        }

        prefs.edit().putString("contact_$cleanName", finalNumber).apply()
        _favoriteContacts.value = loadFavoriteContactsFromPrefs()
        addLog("Kontak disimpan: $name -> $finalNumber")
    }

    override fun removeFavoriteContact(name: String) {
        val cleanName = name.lowercase().trim()
        prefs.edit().remove("contact_$cleanName").apply()
        _favoriteContacts.value = loadFavoriteContactsFromPrefs()
        addLog("Kontak dihapus: $name")
    }

    override fun getPhoneNumberForName(name: String): String? {
        val cleanName = name.lowercase().trim()
        
        // 1. Check favorites first
        val favNumber = _favoriteContacts.value[cleanName]
        if (favNumber != null) {
            Log.d("DataRepository", "Found contact in favorites: $cleanName -> $favNumber")
            return favNumber
        }

        // 2. Query system contacts if READ_CONTACTS permission is granted
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            Log.d("DataRepository", "Searching system contacts for: $cleanName")
            val systemNumber = querySystemContacts(context, cleanName)
            if (systemNumber != null) {
                Log.d("DataRepository", "Found contact in system contacts: $cleanName -> $systemNumber")
                return systemNumber
            }
        }
        return null
    }

    private fun querySystemContacts(context: Context, targetName: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        
        var foundNumber: String? = null
        
        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                if (nameIndex != -1 && numberIndex != -1) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex) ?: ""
                        val number = cursor.getString(numberIndex) ?: ""
                        
                        // Perform case-insensitive match in Kotlin memory (MIUI safe)
                        if (name.lowercase().contains(targetName)) {
                            // Format the phone number to standard international format (starts with 62 instead of 0)
                            val cleanNumber = number.replace(Regex("[^0-9]"), "")
                            foundNumber = if (cleanNumber.startsWith("0")) {
                                "62" + cleanNumber.substring(1)
                            } else {
                                cleanNumber
                            }
                            Log.d("DataRepository", "Resolved contact from phonebook in-memory: $name -> $foundNumber")
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DataRepository", "Failed to query system contacts in-memory", e)
        }
        return foundNumber
    }

    override fun setUnreadQueue(queue: List<WhatsAppMessage>) {
        _unreadQueue.value = queue
        addLog("Antrean pesan diset: ${queue.size} pesan.")
    }

    override fun popUnreadQueue(): WhatsAppMessage? {
        val currentQueue = _unreadQueue.value
        if (currentQueue.isEmpty()) return null
        
        val popped = currentQueue.first()
        _unreadQueue.value = currentQueue.drop(1)
        return popped
    }

    private fun loadFavoriteContactsFromPrefs(): Map<String, String> {
        val contacts = mutableMapOf<String, String>()
        try {
            val all = prefs.all
            for ((key, value) in all) {
                if (key.startsWith("contact_") && value is String) {
                    val name = key.substring("contact_".length)
                    contacts[name] = value
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return contacts
    }

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

    override fun triggerCheckSenderRequest(name: String) {
        _checkSenderRequest.tryEmit(name)
    }

    override fun triggerAssistantFeedback(text: String) {
        _assistantFeedback.tryEmit(text)
    }

    companion object {
        @Volatile
        private var instance: DefaultDataRepository? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = DefaultDataRepository(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): DefaultDataRepository {
            return instance ?: throw IllegalStateException("Repository not initialized. Call initialize(context) first.")
        }
    }
}
