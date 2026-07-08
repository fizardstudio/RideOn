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
import android.net.Uri
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

class AssistantService : Service(), TextToSpeech.OnInitListener, SensorEventListener {

    companion object {
        private const val TAG = "AssistantService"
        private const val CHANNEL_ID = "driving_assistant_channel"
        private const val NOTIFICATION_ID = 1
        private const val UTTERANCE_REPLY_PROMPT = "utterance_reply_prompt"
        private const val UTTERANCE_READ_MESSAGE = "utterance_read_message"
        private const val UTTERANCE_FEEDBACK = "utterance_feedback"
    }

    private val repository by lazy { DefaultDataRepository.getInstance() }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dbHelper by lazy { com.example.drivingassistantapp.data.ChatHistoryDbHelper(this) }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null

    private var activeMessageToReply: WhatsAppMessage? = null
    private var isTtsInitialized = false

    // WhatsApp Send Message Flow variables
    private var sendTargetName: String? = null
    private var sendTargetNumber: String? = null
    private var sendContentText: String? = null

    // Proximity Sensor variables
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    // Reading confirmation variables
    private var isMessageContentRead = false
    private var isExplicitCheckRequest = false

    override fun onCreate() {
        DefaultDataRepository.initialize(applicationContext)
        super.onCreate()
        Log.d(TAG, "Creating AssistantService")

        // Register Proximity Sensor for Hand Wave gesture
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            repository.addLog("Sensor kedekatan aktif (Lambaian Tangan).")
            Log.d(TAG, "Proximity sensor registered successfully")
        } else {
            repository.addLog("Sensor kedekatan tidak didukung di HP ini.")
            Log.w(TAG, "Proximity sensor not available on this device")
        }
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
                    val ask = !isExplicitCheckRequest
                    isExplicitCheckRequest = false // Reset
                    speakAndPromptReply(message, askFirst = ask)
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

        serviceScope.launch {
            repository.speechRate.collectLatest { rate ->
                if (isTtsInitialized) {
                    tts?.setSpeechRate(rate)
                }
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
            val rate = repository.speechRate.value
            tts?.setSpeechRate(rate)
            repository.addLog("TTS berhasil diinisialisasi (Kecepatan: ${rate}x).")
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
                        "prompt_read_message", "prompt_send_content", "prompt_send_confirm" -> {
                            // Start listening for read confirmation / content / confirm
                            mainHandler.postDelayed({
                                startListening(forCommand = false)
                            }, 600)
                        }
                        UTTERANCE_READ_MESSAGE, UTTERANCE_FEEDBACK -> {
                            repository.setAssistantState(AssistantState.IDLE)
                            checkAndProcessNextQueuedMessage()
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

    private fun speakAndPromptReply(message: WhatsAppMessage, askFirst: Boolean) {
        if (!isTtsInitialized) {
            repository.addLog("TTS belum siap, gagal membacakan pesan.")
            return
        }

        activeMessageToReply = message
        
        if (askFirst) {
            isMessageContentRead = false
            val speechText = "Ada pesan baru dari ${message.sender}. Apakah Anda ingin membacanya?"
            repository.addLog("Asisten menawarkan untuk membaca pesan dari ${message.sender}")
            speakTts(speechText, TextToSpeech.QUEUE_FLUSH, "prompt_read_message")
        } else {
            isMessageContentRead = true
            val speechText = if (message.isVoiceNote) {
                "Pesan suara baru dari ${message.sender}. Apakah Anda ingin membuka WhatsApp untuk mendengarnya?"
            } else {
                "Pesan baru dari ${message.sender}. Dia berkata: ${message.text}. Apakah Anda ingin membalas?"
            }
            repository.addLog("Asisten langsung membacakan pesan dari ${message.sender} (VoiceNote: ${message.isVoiceNote})")
            speakTts(speechText, TextToSpeech.QUEUE_FLUSH, UTTERANCE_REPLY_PROMPT)
        }
    }

    private fun triggerVoiceCommand() {
        if (!isTtsInitialized) return
        
        repository.addLog("Asisten mendengarkan perintah suara manual...")
        speakTts("Ya, silakan berbicara", TextToSpeech.QUEUE_FLUSH, "manual_prompt")
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
                playBeepSound()
                // Wait 250ms for the beep sound to finish before opening the microphone
                mainHandler.postDelayed({
                    try {
                        speechRecognizer?.startListening(recognitionIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting speech recognition in delay", e)
                        repository.setAssistantState(AssistantState.IDLE)
                    }
                }, 250)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                repository.setAssistantState(AssistantState.IDLE)
            }
        }
    }

    private fun playBeepSound() {
        try {
            val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
            toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100) // 100ms beep tone
            mainHandler.postDelayed({
                toneGenerator.release()
            }, 400)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar bunyi beep", e)
        }
    }

    private fun handleSpeechResult(text: String, isCommandMode: Boolean) {
        repository.addLog("Mendengar suara: \"$text\"")

        // 1. WhatsApp Send Message Flow
        if (sendTargetNumber != null) {
            val replyText = text.lowercase().trim()
            if (sendContentText == null) {
                if (replyText == "batal" || replyText == "cancel") {
                    speakFeedback("Pengiriman pesan dibatalkan.")
                    resetSendFlowVariables()
                } else {
                    sendContentText = text
                    val confirmSpeech = "Pesan Anda untuk $sendTargetName adalah: $text. Katakan kirim untuk mengirim, ulangi untuk merekam kembali, atau batal untuk membatalkan."
                    speakTts(confirmSpeech, TextToSpeech.QUEUE_FLUSH, "prompt_send_confirm")
                }
            } else {
                if (replyText.contains("kirim") || replyText == "ya" || replyText == "oke" || replyText == "send") {
                    executeWhatsAppSendIntent(sendTargetNumber!!, sendContentText!!)
                    speakFeedback("Membuka WhatsApp untuk mengirim pesan.")
                    resetSendFlowVariables()
                } else if (replyText.contains("ulang") || replyText.contains("edit") || replyText.contains("tulis kembali")) {
                    // Redictate the message content
                    sendContentText = null
                    val promptSpeech = "Silakan katakan kembali isi pesan untuk $sendTargetName."
                    speakTts(promptSpeech, TextToSpeech.QUEUE_FLUSH, "prompt_send_content")
                } else {
                    speakFeedback("Pengiriman pesan dibatalkan.")
                    resetSendFlowVariables()
                }
            }
            return
        }

        // 2. Normal Modes
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
                        speakTts(speechText, TextToSpeech.QUEUE_FLUSH, UTTERANCE_READ_MESSAGE)
                    } else {
                        speakFeedback("Tidak ada pesan WhatsApp terakhir untuk dibaca.")
                    }
                }
                is ParsedCommand.CheckUnread -> {
                    repository.addLog("Mengecek pesan WhatsApp belum terbaca...")
                    isExplicitCheckRequest = true
                    repository.triggerCheckUnreadRequest()
                }
                is ParsedCommand.ReadFromContact -> {
                    repository.addLog("Mengecek pesan dari ${cmd.contactName}...")
                    isExplicitCheckRequest = true
                    repository.triggerCheckSenderRequest(cmd.contactName)
                }
                is ParsedCommand.ReadHistory -> {
                    repository.addLog("Membaca riwayat chat untuk ${cmd.contactName}...")
                    val history = dbHelper.getHistoryForSender(cmd.contactName, 3)
                    if (history.isNotEmpty()) {
                        val header = "Berikut adalah ${history.size} pesan terakhir dari ${cmd.contactName}. "
                        val body = history.mapIndexed { idx, msg ->
                            val prefix = when(idx) {
                                0 -> "Pertama: "
                                1 -> "Kedua: "
                                else -> "Ketiga: "
                            }
                            prefix + if (msg.isVoiceNote) "Pesan suara" else msg.text
                        }.joinToString(". ")
                        speakTts(header + body, TextToSpeech.QUEUE_FLUSH, UTTERANCE_FEEDBACK)
                    } else {
                        speakFeedback("Tidak ditemukan riwayat pesan untuk ${cmd.contactName} di memori Ride On.")
                    }
                }
                is ParsedCommand.SendToContact -> {
                    val phone = repository.getPhoneNumberForName(cmd.contactName)
                    if (phone != null) {
                        sendTargetName = cmd.contactName
                        sendTargetNumber = phone
                        sendContentText = null
                        val promptSpeech = "Apa isi pesan untuk ${cmd.contactName}?"
                        speakTts(promptSpeech, TextToSpeech.QUEUE_FLUSH, "prompt_send_content")
                    } else {
                        // Check if contacts permission is granted
                        val hasContactsPermission = checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val feedback = if (!hasContactsPermission) {
                            "Gagal mencari kontak karena izin akses kontak belum diberikan di aplikasi."
                        } else {
                            "Kontak ${cmd.contactName} tidak ditemukan."
                        }
                        speakFeedback(feedback)
                    }
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
            // WhatsApp Reply Mode or Read Confirmation Mode
            val replyText = text.lowercase().trim()
            val activeMsg = activeMessageToReply

            if (activeMsg != null && !isMessageContentRead) {
                // READ CONFIRMATION PHASE
                if (replyText.contains("ya") || replyText == "buka" || replyText == "boleh" || replyText == "baca") {
                    isMessageContentRead = true
                    val contentSpeech = if (activeMsg.isVoiceNote) {
                        "Pesan suara baru dari ${activeMsg.sender}. Apakah Anda ingin membuka WhatsApp untuk mendengarnya?"
                    } else {
                        "Dia berkata: ${activeMsg.text}. Apakah Anda ingin membalas?"
                    }
                    speakTts(contentSpeech, TextToSpeech.QUEUE_FLUSH, UTTERANCE_REPLY_PROMPT)
                } else {
                    speakFeedback("Baik, tetap fokus berkendara.")
                    resetSendFlowVariables()
                    activeMessageToReply = null
                    isMessageContentRead = false
                }
                return
            }

            // REPLY PHASE
            if (activeMsg != null && activeMsg.isVoiceNote) {
                // Voice Note Flow: Yes/No to open WhatsApp
                if (replyText.contains("ya") || replyText == "buka" || replyText == "boleh" || replyText == "open") {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        speakFeedback("Membuka WhatsApp.")
                    } else {
                        speakFeedback("Gagal membuka WhatsApp, aplikasi tidak terpasang.")
                    }
                } else {
                    speakFeedback("Baik, tetap fokus berkendara.")
                }
            } else {
                // Normal Text Message Reply Flow
                if (replyText.contains("batal") || replyText == "tidak" || replyText == "nggak" || replyText == "no") {
                    speakFeedback("Baik, pesan tidak dibalas.")
                } else if (replyText == "ya" || replyText == "boleh" || replyText == "balas") {
                    // If they say "Yes" but didn't state the content, ask again
                    speakTts("Silakan katakan isi balasannya", TextToSpeech.QUEUE_FLUSH, UTTERANCE_REPLY_PROMPT)
                } else if (replyText.contains("ulang") || replyText.contains("edit") || replyText.contains("tulis kembali")) {
                    // Redictate the text reply
                    speakTts("Silakan katakan kembali balasannya", TextToSpeech.QUEUE_FLUSH, UTTERANCE_REPLY_PROMPT)
                } else {
                    // Perform quick reply via PendingIntent RemoteInput
                    if (activeMsg != null && activeMsg.replyAction != null) {
                        sendNotificationReply(activeMsg.replyAction, text)
                        speakFeedback("Membalas ${activeMsg.sender}: \"$text\". Pesan terkirim.")
                    } else {
                        speakFeedback("Gagal membalas, tautan notifikasi tidak ditemukan.")
                    }
                }
            }
        }
    }

    private fun checkAndProcessNextQueuedMessage() {
        val next = repository.popUnreadQueue()
        if (next != null) {
            mainHandler.postDelayed({
                // Read next messages from manual check queue directly
                speakAndPromptReply(next, askFirst = false)
            }, 1200)
        }
    }

    // SensorEventListener overrides for hand wave proximity detection
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val distance = event.values[0]
        val maxRange = event.sensor.maximumRange

        // Proximity sensor returns 0 when close. Max range means far.
        val isClose = distance < 5.0f && distance < maxRange

        if (isClose) {
            val currentState = repository.assistantState.value
            
            // Barge-In: Waving hand while assistant is active instantly interrupts and opens mic
            if (currentState == AssistantState.SPEAKING || currentState == AssistantState.LISTENING_REPLY || currentState == AssistantState.LISTENING_COMMAND) {
                repository.addLog("Lambaian tangan: Memotong pembicaraan asisten (Barge-in).")
                try {
                    tts?.stop() // Stop TTS speaking instantly
                    speechRecognizer?.cancel() // Stop active listening session
                } catch (e: Exception) {
                    Log.e(TAG, "Error performing Barge-in interruption", e)
                }
                resetSendFlowVariables()
                // Directly start listening for new command with a prompt beep
                startListening(forCommand = true)
            } else if (currentState == AssistantState.IDLE) {
                repository.addLog("Lambaian tangan mendeteksi pemicu suara.")
                triggerVoiceCommand()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resetSendFlowVariables() {
        sendTargetName = null
        sendTargetNumber = null
        sendContentText = null
    }

    private fun executeWhatsAppSendIntent(phone: String, message: String) {
        try {
            repository.setAutoSendPending(true) // Enable Accessibility Service click interceptor
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.whatsapp")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            repository.setAutoSendPending(false)
            Log.e(TAG, "Gagal meluncurkan intent kirim WhatsApp", e)
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
        speakTts(text, TextToSpeech.QUEUE_FLUSH, UTTERANCE_FEEDBACK)
    }

    private fun speakTts(text: String, queueMode: Int, utteranceId: String?) {
        if (tts == null || !isTtsInitialized) return

        // 1. Auto-Volume Safety Check
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val percentage = currentVolume.toFloat() / maxVolume
            if (percentage < 0.40f) {
                val safeVolume = (maxVolume * 0.65f).toInt()
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, safeVolume, 0)
                repository.addLog("Volume media dinaikkan otomatis ke tingkat aman (65%).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memverifikasi volume media HP", e)
        }

        // 2. Language Auto-Switch Detection
        val isEnglish = isEnglishText(text)
        if (isEnglish) {
            tts?.setLanguage(java.util.Locale.US)
        } else {
            tts?.setLanguage(java.util.Locale("id", "ID"))
        }

        // 3. Play TTS speech
        tts?.speak(text, queueMode, null, utteranceId)
    }

    private fun isEnglishText(text: String): Boolean {
        val englishWords = setOf(
            "the", "and", "you", "are", "otw", "wait", "busy", "call", "later", "please", 
            "thanks", "hello", "hi", "ok", "yes", "no", "going", "road", "busy", "drive", "driving"
        )
        val words = text.lowercase().split(Regex("[^a-zA-Z]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        
        var matches = 0
        for (w in words) {
            if (w in englishWords) matches++
        }
        val ratio = matches.toFloat() / words.size
        return ratio >= 0.20f || (words.size <= 3 && matches >= 1)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
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
                    resetSendFlowVariables()
                    repository.setAssistantState(AssistantState.IDLE)
                    return
                }
            }
            
            resetSendFlowVariables()
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
