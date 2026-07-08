package com.example.drivingassistantapp.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.drivingassistantapp.data.DefaultDataRepository

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppAccessService"
    }

    private val repository by lazy {
        DefaultDataRepository.initialize(applicationContext)
        DefaultDataRepository.getInstance()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        if (packageName != "com.whatsapp") return

        // Only trigger if an auto-send is pending from Ride On
        if (!repository.autoSendPending.value) return

        val rootNode = rootInActiveWindow ?: return
        
        // Find send button
        val sendButton = findSendButton(rootNode)
        if (sendButton != null) {
            Log.d(TAG, "Found WhatsApp send button! Automating click.")
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            repository.setAutoSendPending(false) // Reset flag

            // Wait 600ms for WhatsApp to process the sending animation, then press Back to return to Ride On
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 600)
        }
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Search by view ID
        val sendByIdList = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (!sendByIdList.isNullOrEmpty()) {
            for (n in sendByIdList) {
                if (n.isClickable) return n
            }
        }

        // 2. Fallback: Search by content description (English & Indonesian support)
        return dfsSearchSendButton(node)
    }

    private fun dfsSearchSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc == "kirim" || desc == "send") {
            if (node.isClickable) return node
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = dfsSearchSendButton(child)
                if (result != null) return result
            }
        }
        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
    }
}
