package com.example.drivingassistantapp.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.drivingassistantapp.data.DefaultDataRepository
import com.example.drivingassistantapp.data.WhatsAppMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MyNotificationListener"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
    }

    private val repository by lazy { DefaultDataRepository.getInstance() }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        DefaultDataRepository.initialize(applicationContext)
        super.onCreate()
        Log.d(TAG, "Notification Listener Service created")
        
        // Listen for manual requests to check all unread messages
        serviceScope.launch {
            repository.checkUnreadRequest.collect {
                checkActiveUnreadMessages()
            }
        }

        // Listen for requests to check messages from a specific contact
        serviceScope.launch {
            repository.checkSenderRequest.collect { name ->
                checkMessagesFromSender(name)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected successfully")
        repository.addLog("Akses Notifikasi terhubung & aktif.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        repository.addLog("Akses Notifikasi terputus.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName == WHATSAPP_PACKAGE) {
            val notification = sbn.notification
            
            // Check if it is a group summary notification
            val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isSummary) {
                Log.d(TAG, "Detected WhatsApp summary notification. Searching child notifications.")
                val activeNotifications = activeNotifications
                if (activeNotifications != null) {
                    for (activeSbn in activeNotifications) {
                        if (activeSbn.packageName == WHATSAPP_PACKAGE &&
                            activeSbn.groupKey == sbn.groupKey &&
                            (activeSbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0) {
                            // Process the child message notification instead
                            processNotification(activeSbn)
                            return
                        }
                    }
                }
            } else {
                processNotification(sbn)
            }
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        
        // Skip system warnings or general WhatsApp category notifications
        if (title.isEmpty() || title == "WhatsApp") {
            return
        }

        // Parse using MessagingStyle to gather multiple messages, detect self-replies, and identify Voice Notes
        val parsed = parseMessagingStyle(notification)
        if (parsed == null) {
            // Ignored because it's a self-reply, empty, or has no new incoming messages
            return
        }

        val (senderName, messagesList, isVoiceNote) = parsed
        val combinedText = messagesList.joinToString(". ") // Period creates a natural TTS pause

        Log.d(TAG, "Processing WhatsApp message from $senderName: \"$combinedText\" (VoiceNote: $isVoiceNote)")

        val replyAction = findReplyAction(notification)
        if (replyAction != null) {
            Log.d(TAG, "Found reply action for WhatsApp notification from $senderName")
            val message = WhatsAppMessage(
                sender = senderName,
                text = combinedText,
                replyAction = replyAction,
                isVoiceNote = isVoiceNote
            )
            
            // 1. Check Driving Mode (Silent Auto-Reply)
            if (repository.drivingModeEnabled.value) {
                repository.addLog("Membalas otomatis pesan dari $senderName (Mode Menyetir)")
                repository.triggerAssistantFeedback("Membalas otomatis pesan dari $senderName")
                sendNotificationReply(replyAction, repository.autoReplyTemplate.value)
                return
            }

            // 2. Normal Auto-Read checking
            if (repository.autoReadEnabled.value) {
                repository.setLastMessage(message)
            } else {
                repository.addLog("Pesan masuk dari $senderName disimpan (Auto-Read Nonaktif).")
            }
        } else {
            Log.w(TAG, "No reply action found for WhatsApp notification from $senderName")
            repository.addLog("Gagal menangkap aksi balas WA untuk $senderName")
        }
    }

    private fun checkActiveUnreadMessages() {
        Log.d(TAG, "Checking active unread messages manually requested by user")
        val activeNotifications = activeNotifications
        
        if (activeNotifications.isNullOrEmpty()) {
            repository.triggerAssistantFeedback("Tidak ada pesan WhatsApp baru yang belum terbaca.")
            repository.addLog("Pemeriksaan pesan: tidak ada notifikasi aktif.")
            return
        }

        val queue = mutableListOf<WhatsAppMessage>()

        // Search for all WhatsApp notifications containing valid unread messages
        for (sbn in activeNotifications) {
            if (sbn.packageName == WHATSAPP_PACKAGE) {
                val notification = sbn.notification
                
                // Skip summary groups
                val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                if (isSummary) continue

                val parsed = parseMessagingStyle(notification)
                if (parsed != null) {
                    val (senderName, messagesList, isVoiceNote) = parsed
                    val combinedText = messagesList.joinToString(". ")
                    val replyAction = findReplyAction(notification)
                    
                    if (replyAction != null) {
                        Log.d(TAG, "Found active unread message from $senderName: $combinedText")
                        queue.add(WhatsAppMessage(
                            sender = senderName,
                            text = combinedText,
                            replyAction = replyAction,
                            isVoiceNote = isVoiceNote
                        ))
                    }
                }
            }
        }

        if (queue.isNotEmpty()) {
            repository.addLog("Pemeriksaan pesan: ditemukan ${queue.size} obrolan belum dibaca.")
            
            val countText = "Anda memiliki ${queue.size} percakapan belum terbaca."
            repository.triggerAssistantFeedback(countText)
            
            // Wait 2.2 seconds for feedback to complete, then push to the queue and trigger the first message
            serviceScope.launch {
                kotlinx.coroutines.delay(2200L)
                repository.setUnreadQueue(queue)
                val first = repository.popUnreadQueue()
                if (first != null) {
                    repository.setLastMessage(first)
                }
            }
        } else {
            repository.triggerAssistantFeedback("Tidak ada pesan WhatsApp baru yang belum terbaca.")
            repository.addLog("Pemeriksaan pesan: tidak ada pesan WhatsApp belum terbalas.")
        }
    }

    private fun checkMessagesFromSender(senderNameQuery: String) {
        val cleanQuery = senderNameQuery.lowercase().trim()
        Log.d(TAG, "Checking messages from sender containing query: '$cleanQuery'")
        
        val activeNotifications = activeNotifications
        if (activeNotifications.isNullOrEmpty()) {
            repository.triggerAssistantFeedback("Tidak ada pesan baru dari $senderNameQuery.")
            return
        }

        for (sbn in activeNotifications) {
            if (sbn.packageName == WHATSAPP_PACKAGE) {
                val notification = sbn.notification
                val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                if (isSummary) continue

                val parsed = parseMessagingStyle(notification)
                if (parsed != null) {
                    val (senderName, messagesList, isVoiceNote) = parsed
                    
                    // Match contact name
                    if (senderName.lowercase().contains(cleanQuery)) {
                        val combinedText = messagesList.joinToString(". ")
                        val replyAction = findReplyAction(notification)
                        
                        if (replyAction != null) {
                            Log.d(TAG, "Found target unread message from $senderName: $combinedText")
                            repository.addLog("Membaca pesan dari $senderName")
                            
                            val message = WhatsAppMessage(
                                sender = senderName,
                                text = combinedText,
                                replyAction = replyAction,
                                isVoiceNote = isVoiceNote
                            )
                            repository.setLastMessage(message)
                            return
                        }
                    }
                }
            }
        }

        repository.triggerAssistantFeedback("Tidak ada pesan baru dari $senderNameQuery.")
    }

    private fun parseMessagingStyle(notification: Notification): Triple<String, List<String>, Boolean>? {
        val extras = notification.extras ?: return null
        
        // 1. Group Filtering Check
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val groupTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""
        if (isGroup && repository.ignoreGroupsEnabled.value) {
            Log.d(TAG, "Ignoring group chat notification because ignoreGroupsEnabled is true.")
            return null
        }

        val messagesArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messagesArray.isNullOrEmpty()) {
            // Fallback for simple/legacy notification formats
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val textLower = text.lowercase()
            
            // Check self-reply prefix
            if (text.isEmpty() || textLower.startsWith("anda: ") || textLower.startsWith("you: ") || textLower.contains("pesan baru")) {
                return null
            }
            val isVoice = textLower.contains("pesan suara") || textLower.contains("voice note") || textLower.contains("voice message")
            return Triple(title, listOf(text), isVoice)
        }

        val incomingMessages = mutableListOf<String>()
        var lastMessageIsFromSelf = false
        var lastSender = ""
        var isVoiceNote = false

        for (parcelable in messagesArray) {
            if (parcelable is Bundle) {
                val text = parcelable.getCharSequence("text")?.toString() ?: ""
                val textLower = text.lowercase()
                val senderBundle = parcelable.getBundle("sender")
                val sender = parcelable.getCharSequence("sender")?.toString() 
                    ?: senderBundle?.getCharSequence("name")?.toString()
                
                // If sender name matches self keywords or is null (which stands for user/self in MessagingStyle)
                if (sender == null || sender.lowercase() == "anda" || sender.lowercase() == "you") {
                    lastMessageIsFromSelf = true
                    incomingMessages.clear() // Reset incoming messages since user has replied to this thread
                } else {
                    lastMessageIsFromSelf = false
                    lastSender = sender
                    if (text.isNotEmpty()) {
                        incomingMessages.add(text)
                    }
                    // Check if message is marked as voice note
                    if (textLower.contains("pesan suara") || textLower.contains("voice note") || textLower.contains("voice message")) {
                        isVoiceNote = true
                    }
                }
            }
        }

        if (lastMessageIsFromSelf || incomingMessages.isEmpty()) {
            return null
        }

        // Format sender title based on whether it is a group chat or DM
        val displaySender = if (isGroup && groupTitle.isNotEmpty() && lastSender.isNotEmpty()) {
            "$lastSender di grup $groupTitle"
        } else if (lastSender.isNotEmpty()) {
            lastSender
        } else {
            extras.getString(Notification.EXTRA_TITLE) ?: ""
        }

        return Triple(displaySender, incomingMessages, isVoiceNote)
    }

    private fun sendNotificationReply(action: Notification.Action, replyText: String) {
        val intent = Intent()
        val bundle = Bundle()
        val remoteInputs = action.remoteInputs
        if (remoteInputs != null) {
            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
        
        try {
            action.actionIntent.send(this, 0, intent)
            Log.d(TAG, "Sent driving mode auto-reply successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending auto-reply in driving mode", e)
        }
    }

    private fun findReplyAction(n: Notification): Notification.Action? {
        // 1. Direct Actions check
        val actions = n.actions
        if (actions != null) {
            for (action in actions) {
                if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                    val title = action.title.toString().lowercase()
                    if (title.contains("balas") || title.contains("reply")) {
                        return action
                    }
                }
            }
            for (action in actions) {
                if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                    return action
                }
            }
        }

        // 2. WearableExtender check
        try {
            val wearableExtender = Notification.WearableExtender(n)
            val wearableActions = wearableExtender.actions
            if (wearableActions != null) {
                for (action in wearableActions) {
                    if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                        return action
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving WearableExtender actions", e)
        }

        return null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
