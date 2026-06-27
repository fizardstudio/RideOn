package com.example.drivingassistantapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.Notification
import android.app.RemoteInput
import com.example.drivingassistantapp.MainActivity
import com.example.drivingassistantapp.data.AssistantState
import com.example.drivingassistantapp.data.DefaultDataRepository
import com.example.drivingassistantapp.data.WhatsAppMessage
import com.example.drivingassistantapp.utils.AssistantCommandParser
import com.example.drivingassistantapp.utils.ParsedCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class AssistantService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AssistantService"
        private const val CHANNEL_ID = "driving_assistant_channel"
        private const val NOTIFICATION_ID = 1
        private const val UTTERANCE_REPLY_PROMPT = "utterance_reply_prompt"
        private const val UTTERANCE_READ_MESSAGE = "utterance_read_message"
        private const val UTTERANCE_FEEDBACK = "utterance_feedback"
    }

    private val repository = DefaultDataRepository.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null

    private var activeMessageToReply: WhatsAppMessage? = null
    private var isTtsInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating AssistantService")
        startServiceForeground()
        initializeSpeechComponents()
        observeDataRepository()
    }

    private fun startServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driving Assistant Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Asisten Berkendara Aktif")
            .setContentText("Siap mendengarkan WhatsApp & navigasi")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initializeSpeechComponents() {
        // Initialize Text To Speech
        tts = TextToSpeech(this, this)
        
        // Initialize Speech Recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(SpeechListener())
        } else {
            repository.addLog("Perekam suara tidak tersedia di perangkat ini.")
        }

        // Prepare recognition intent with robust Indonesian settings and silence limits
        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // Adjust silence duration to prevent early cutoff (in milliseconds)
            putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 3000L)
            putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
            putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
        }
    }

    private fun observeDataRepository() {
        serviceScope.launch {
            repository.lastMessage.collectLatest { message ->
                if (message != null) {
                    // Consume the message so it doesn't trigger again on service relaunch
                    repository.setLastMessage(null)
                    speakAndPromptReply(message)
                }
            }
        }

        serviceScope.launch {
            repository.voiceCommandRequest.collect {
                triggerVoiceCommand()
            }
        }

        serviceScope.launch {
            repository.assistantFeedback.collect { feedback ->
                speakFeedback(feedback)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("id-ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Indonesian language not supported. Falling back to default locale.")
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsInitialized = true
            setupTtsProgressListener()
            repository.addLog("TTS berhasil diinisialisasi.")
        } else {
            repository.addLog("Gagal menginisialisasi TTS.")
        }
    }

    private fun setupTtsProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                repository.setAssistantState(AssistantState.SPEAKING)
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    when (utteranceId) {
                        UTTERANCE_REPLY_PROMPT -> {
                            // Wait 600ms for TTS audio channel to settle, then start listening for reply
                            mainHandler.postDelayed({
                                startListening(forCommand = false)
                            }, 600)
                        }
                        "manual_prompt" -> {
                            // Start listening for manual command immediately after prompt finishes
                            startListening(forCommand = true)
                        }
                        UTTERANCE_READ_MESSAGE, UTTERANCE_FEEDBACK -> {
                            // Return to idle
                            repository.setAssistantState(AssistantState.IDLE)
                        }
                        else -> {
                            repository.setAssistantState(AssistantState.IDLE)
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                repository.setAssistantState(AssistantState.IDLE)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                repository.setAssistantState(AssistantState.IDLE)
            }
        })
    }

    private fun speakAndPromptReply(message: WhatsAppMessage) {
        if (!isTtsInitialized) {
            repository.addLog("TTS belum siap, gagal membacakan pesan.")
            return
        }

        activeMessageToReply = message
        val speechText = "Pesan baru dari ${message.sender}. Dia berkata: ${message.text}. Apakah Anda ingin membalas?"
        repository.addLog("Asisten membacakan pesan dari ${message.sender}")
        
        tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_REPLY_PROMPT)
    }

    private fun triggerVoiceCommand() {
        if (!isTtsInitialized) return
        
        repository.addLog("Asisten mendengarkan perintah suara manual...")
        tts?.speak("Ya, silakan berbicara", TextToSpeech.QUEUE_FLUSH, null, "manual_prompt")
    }

    private fun startListening(forCommand: Boolean) {
        if (speechRecognizer == null) {
            repository.addLog("Perekam suara tidak siap.")
            return
        }

        repository.setAssistantState(
            if (forCommand) AssistantState.LISTENING_COMMAND else AssistantState.LISTENING_REPLY
        )
        
        mainHandler.post {
            try {
                speechRecognizer?.startListening(recognitionIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                repository.setAssistantState(AssistantState.IDLE)
            }
        }
    }

    private fun handleSpeechResult(text: String, isCommandMode: Boolean) {
        repository.addLog("Mendengar suara: \"$text\"")

        if (isCommandMode) {
            // General Command Mode
            when (val cmd = AssistantCommandParser.parse(text)) {
                is ParsedCommand.Cancel -> {
                    speakFeedback("Perintah dibatalkan.")
                }
                is ParsedCommand.ReadMessage -> {
                    val lastMsg = activeMessageToReply
                    if (lastMsg != null) {
                        val speechText = "Pesan terakhir dari ${lastMsg.sender}: ${lastMsg.text}"
                        tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_READ_MESSAGE)
                    } else {
                        speakFeedback("Tidak ada pesan WhatsApp terakhir untuk dibaca.")
                    }
                }
                is ParsedCommand.CheckUnread -> {
                    repository.addLog("Mengecek pesan WhatsApp belum terbaca...")
                    repository.triggerCheckUnreadRequest()
                }
                is ParsedCommand.Navigate -> {
                    speakFeedback("Membuka rute ke ${cmd.location}")
                    AssistantCommandParser.executeNavigation(this, cmd.location)
                }
                is ParsedCommand.Unknown -> {
                    speakFeedback("Maaf, saya tidak mengerti perintah: $text")
                }
            }
        } else {
            // WhatsApp Reply Mode
            val replyText = text.lowercase().trim()
            if (replyText.contains("batal") || replyText == "tidak" || replyText == "nggak" || replyText == "no") {
                speakFeedback("Baik, pesan tidak dibalas.")
            } else if (replyText == "ya" || replyText == "boleh" || replyText == "balas") {
                // If they say "Yes" but didn't state the content, ask again
                tts?.speak("Silakan katakan isi balasannya", TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_REPLY_PROMPT)
            } else {
                // Perform quick reply via PendingIntent RemoteInput
                val activeMsg = activeMessageToReply
                if (activeMsg != null && activeMsg.replyAction != null) {
                    sendNotificationReply(activeMsg.replyAction, text)
                    speakFeedback("Membalas ${activeMsg.sender}: \"$text\". Pesan terkirim.")
                } else {
                    speakFeedback("Gagal membalas, tautan notifikasi tidak ditemukan.")
                }
            }
        }
    }

    private fun sendNotificationReply(action: Notification.Action, replyText: String) {
        val intent = Intent()
        val bundle = Bundle()
        
        for (remoteInput in action.remoteInputs) {
            bundle.putCharSequence(remoteInput.resultKey, replyText)
        }
        
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
        
        try {
            action.actionIntent.send(this, 0, intent)
            repository.addLog("Sistem membalas WhatsApp dengan teks: \"$replyText\"")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger Quick Reply action intent", e)
            repository.addLog("Gagal mengirim balasan WhatsApp: ${e.message}")
        }
    }

    private fun speakFeedback(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_FEEDBACK)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
        Log.d(TAG, "AssistantService destroyed")
    }

    // Inner class recognition listener
    private inner class SpeechListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "SpeechRecognizer ready")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech began")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error"
            }
            Log.w(TAG, "SpeechRecognizer error: $errorMsg")
            
            // Only speak feedback for speech timeout or no match if we were expecting a reply
            if (repository.assistantState.value == AssistantState.LISTENING_REPLY) {
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                    speakFeedback("Tidak mendengar balasan pesan.")
                }
            }
            
            repository.setAssistantState(AssistantState.IDLE)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                val currentState = repository.assistantState.value
                val isCommandMode = currentState == AssistantState.LISTENING_COMMAND
                handleSpeechResult(text, isCommandMode)
            } else {
                repository.setAssistantState(AssistantState.IDLE)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
