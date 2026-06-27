package com.example.drivingassistantapp.service

import android.app.Notification
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

    private val repository = DefaultDataRepository.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Notification Listener Service created")
        
        // Listen for manual requests to check unread messages
        serviceScope.launch {
            repository.checkUnreadRequest.collect {
                checkActiveUnreadMessages()
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

        // Parse using MessagingStyle to gather multiple messages and detect self-replies
        val parsed = parseMessagingStyle(notification)
        if (parsed == null) {
            // Ignored because it's a self-reply, empty, or has no new incoming messages
            return
        }

        val (senderName, messagesList) = parsed
        val combinedText = messagesList.joinToString(". ") // Period creates a natural TTS pause

        Log.d(TAG, "Processing WhatsApp message from $senderName: \"$combinedText\"")

        val replyAction = findReplyAction(notification)
        if (replyAction != null) {
            Log.d(TAG, "Found reply action for WhatsApp notification from $senderName")
            val message = WhatsAppMessage(
                sender = senderName,
                text = combinedText,
                replyAction = replyAction
            )
            
            repository.setLastMessage(message)
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

        // Search for the first WhatsApp notification containing a valid unread message
        for (sbn in activeNotifications) {
            if (sbn.packageName == WHATSAPP_PACKAGE) {
                val notification = sbn.notification
                
                // Skip summary groups
                val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                if (isSummary) continue

                val parsed = parseMessagingStyle(notification)
                if (parsed != null) {
                    val (senderName, messagesList) = parsed
                    val combinedText = messagesList.joinToString(". ")
                    val replyAction = findReplyAction(notification)
                    
                    if (replyAction != null) {
                        Log.d(TAG, "Found active unread message from $senderName: $combinedText")
                        repository.addLog("Membaca pesan belum terbalas dari $senderName")
                        
                        val message = WhatsAppMessage(
                            sender = senderName,
                            text = combinedText,
                            replyAction = replyAction
                        )
                        repository.setLastMessage(message)
                        return // Read only the first one to avoid overlapping speech
                    }
                }
            }
        }

        // If we looped through all active notifications and found no unread WhatsApp messages
        repository.triggerAssistantFeedback("Tidak ada pesan WhatsApp baru yang belum terbaca.")
        repository.addLog("Pemeriksaan pesan: tidak ada pesan WhatsApp belum terbalas.")
    }

    private fun parseMessagingStyle(notification: Notification): Pair<String, List<String>>? {
        val extras = notification.extras ?: return null
        
        // Check if this is a group conversation
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val groupTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""

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
            return Pair(title, listOf(text))
        }

        val incomingMessages = mutableListOf<String>()
        var lastMessageIsFromSelf = false
        var lastSender = ""

        for (parcelable in messagesArray) {
            if (parcelable is Bundle) {
                val text = parcelable.getCharSequence("text")?.toString() ?: ""
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

        return Pair(displaySender, incomingMessages)
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
