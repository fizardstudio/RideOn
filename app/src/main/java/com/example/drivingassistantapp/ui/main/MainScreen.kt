package com.example.drivingassistantapp.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.drivingassistantapp.data.AssistantState
import com.example.drivingassistantapp.data.DefaultDataRepository
import com.example.drivingassistantapp.service.WhatsAppAccessibilityService

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository.getInstance()) },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoReadEnabled by viewModel.autoReadEnabled.collectAsStateWithLifecycle()
    val drivingModeEnabled by viewModel.drivingModeEnabled.collectAsStateWithLifecycle()
    val ignoreGroupsEnabled by viewModel.ignoreGroupsEnabled.collectAsStateWithLifecycle()
    val autoReplyTemplate by viewModel.autoReplyTemplate.collectAsStateWithLifecycle()
    val favoriteContacts by viewModel.favoriteContacts.collectAsStateWithLifecycle()
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    
    // Check permission state in UI
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(isNotificationServiceEnabled(context))
    }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAccessibilityPermission by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }

    // Permission request launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    // Refresh permissions on resume
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasNotificationPermission = isNotificationServiceEnabled(context)
                hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
            }
        }
        onDispose {}
    }

    DashboardContent(
        state = state,
        autoReadEnabled = autoReadEnabled,
        drivingModeEnabled = drivingModeEnabled,
        ignoreGroupsEnabled = ignoreGroupsEnabled,
        autoReplyTemplate = autoReplyTemplate,
        speechRate = speechRate,
        favoriteContacts = favoriteContacts,
        hasMicPermission = hasMicPermission,
        hasNotificationPermission = hasNotificationPermission,
        hasContactsPermission = hasContactsPermission,
        hasAccessibilityPermission = hasAccessibilityPermission,
        onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onRequestNotificationPermission = {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        },
        onRequestContactsPermission = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
        onRequestAccessibilityPermission = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onMicClicked = { viewModel.onMicClicked() },
        onStartService = { viewModel.startService(context) },
        onStopService = { viewModel.stopService(context) },
        onToggleAutoRead = { viewModel.setAutoReadEnabled(it) },
        onToggleDrivingMode = { viewModel.setDrivingModeEnabled(it) },
        onToggleIgnoreGroups = { viewModel.setIgnoreGroupsEnabled(it) },
        onSaveTemplate = { viewModel.setAutoReplyTemplate(it) },
        onSetSpeechRate = { viewModel.setSpeechRate(it) },
        onPlayVoiceTutorial = { viewModel.playVoiceTutorial() },
        onAddContact = { name, phone -> viewModel.addFavoriteContact(name, phone) },
        onRemoveContact = { name -> viewModel.removeFavoriteContact(name) },
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    state: MainUiState,
    autoReadEnabled: Boolean,
    drivingModeEnabled: Boolean,
    ignoreGroupsEnabled: Boolean,
    autoReplyTemplate: String,
    speechRate: Float,
    favoriteContacts: Map<String, String>,
    hasMicPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasContactsPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onMicClicked: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onToggleAutoRead: (Boolean) -> Unit,
    onToggleDrivingMode: (Boolean) -> Unit,
    onToggleIgnoreGroups: (Boolean) -> Unit,
    onSaveTemplate: (String) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onPlayVoiceTutorial: () -> Unit,
    onAddContact: (String, String) -> Unit,
    onRemoveContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val darkBackground = Color(0xFF0B0F19)
    val cardBackground = Color(0xFF171E2E)
    val neonCyan = Color(0xFF00F2FE)
    val neonGreen = Color(0xFF00F5A0)
    val neonRed = Color(0xFFFF0844)

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title Header
        Text(
            text = "RIDE ON",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )

        Text(
            text = "Asisten Suara WhatsApp Berkendara",
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. Unified Configuration Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Konfigurasi Asisten",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Toggle Auto-Read
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Read WhatsApp",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Membacakan pesan masuk secara otomatis",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = autoReadEnabled,
                        onCheckedChange = onToggleAutoRead,
                        colors = switchColors()
                    )
                }

                HorizontalDivider(color = Color(0xFF1E283A), modifier = Modifier.padding(vertical = 8.dp))

                // Toggle Ignore Group Chats
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Abaikan Chat Grup",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Mendiamkan semua notifikasi dari grup",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = ignoreGroupsEnabled,
                        onCheckedChange = onToggleIgnoreGroups,
                        colors = switchColors()
                    )
                }

                HorizontalDivider(color = Color(0xFF1E283A), modifier = Modifier.padding(vertical = 8.dp))

                // Toggle Driving Mode (Auto-Reply)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Menyetir (Auto-Reply)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Mengirim pesan template otomatis secara hening",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = drivingModeEnabled,
                        onCheckedChange = onToggleDrivingMode,
                        colors = switchColors()
                    )
                }

                HorizontalDivider(color = Color(0xFF1E283A), modifier = Modifier.padding(vertical = 8.dp))

                // Speech Rate Slider
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kecepatan Bicara Asisten",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${String.format("%.1f", speechRate)}x",
                            color = neonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = speechRate,
                        onValueChange = onSetSpeechRate,
                        valueRange = 0.8f..1.8f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = neonCyan,
                            activeTrackColor = neonCyan,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }

                // Custom Auto-Reply message editor (shows only when drivingMode is on)
                if (drivingModeEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    var templateText by remember { mutableStateOf(autoReplyTemplate) }
                    
                    OutlinedTextField(
                        value = templateText,
                        onValueChange = { templateText = it },
                        label = { Text("Pesan Auto-Reply", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = neonCyan,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onSaveTemplate(templateText) },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("SIMPAN TEMPLATE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }

        // 2. Permission Banners
        if (!hasMicPermission || !hasNotificationPermission || !hasContactsPermission || !hasAccessibilityPermission) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C222E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Izin Diperlukan",
                        color = neonRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aplikasi memerlukan izin Mikrofon, Notifikasi, Kontak, dan Aksesibilitas agar asisten dapat membacakan, membalas, dan mengirim WhatsApp secara otomatis.",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                        ) {
                            if (!hasMicPermission) {
                                Button(
                                    onClick = onRequestMicPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Izin Mic", fontSize = 9.sp, color = Color.White)
                                }
                            }
                            if (!hasNotificationPermission) {
                                Button(
                                    onClick = onRequestNotificationPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Notifikasi", fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                        ) {
                            if (!hasContactsPermission) {
                                Button(
                                    onClick = onRequestContactsPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Izin Kontak", fontSize = 9.sp, color = Color.White)
                                }
                            }
                            if (!hasAccessibilityPermission) {
                                Button(
                                    onClick = onRequestAccessibilityPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Auto-Kirim", fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Service Control Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Layanan Latar Belakang", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row {
                Button(
                    onClick = onStartService,
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("START", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Button(
                    onClick = onStopService,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // 3b. Voice Guide / Panduan Suara Button Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2A)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Cara Penggunaan Ride On",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Ketuk tombol suara untuk mendengar panduan penggunaan hands-free",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                Button(
                    onClick = onPlayVoiceTutorial,
                    colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("PUTAR PANDUAN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }

        // 4. Proximity Wave / Mic Visualizer Box
        Box(
            modifier = Modifier
                .size(170.dp)
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val isListening = state.assistantState == AssistantState.LISTENING_COMMAND || state.assistantState == AssistantState.LISTENING_REPLY
            val isSpeaking = state.assistantState == AssistantState.SPEAKING
            
            if (isListening || isSpeaking) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        animation = twistAnimation(isListening),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0.0f,
                    animationSpec = infiniteRepeatable(
                        animation = twistAnimation(isListening),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isListening) neonCyan else neonGreen).copy(alpha = alpha)
                        )
                        .align(Alignment.Center)
                )
            }

            val buttonColor = when (state.assistantState) {
                AssistantState.LISTENING_COMMAND, AssistantState.LISTENING_REPLY -> neonCyan
                AssistantState.SPEAKING -> neonGreen
                AssistantState.IDLE -> Color(0xFF232D42)
            }

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(buttonColor, buttonColor.copy(alpha = 0.8f))))
                    .clickable(enabled = hasMicPermission) { onMicClicked() },
                contentAlignment = Alignment.Center
            ) {
                MicrophoneIcon(
                    modifier = Modifier.size(40.dp),
                    color = if (state.assistantState == AssistantState.IDLE) Color.White else Color.Black
                )
            }
        }

        // 5. Assistant Status Display
        val statusText = when (state.assistantState) {
            AssistantState.IDLE -> "Asisten Siaga Lambaian Tangan"
            AssistantState.SPEAKING -> "Asisten sedang Berbicara..."
            AssistantState.LISTENING_COMMAND -> "Mendengarkan Perintah..."
            AssistantState.LISTENING_REPLY -> "Mendengarkan Tanggapan..."
        }
        val statusColor = when (state.assistantState) {
            AssistantState.IDLE -> Color.Gray
            AssistantState.SPEAKING -> neonGreen
            else -> neonCyan
        }

        Text(
            text = statusText,
            color = statusColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 6. Favorite Contacts Manager Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            var contactName by remember { mutableStateOf("") }
            var contactPhone by remember { mutableStateOf("") }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Daftar Kontak Favorit",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Daftarkan nama panggilan lisan dengan nomor WhatsApp",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Nama", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = neonCyan,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonCyan,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("No. WhatsApp", fontSize = 11.sp) },
                        placeholder = { Text("0812...", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = neonCyan,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonCyan,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1.8f)
                    )
                    Button(
                        onClick = {
                            if (contactName.isNotEmpty() && contactPhone.isNotEmpty()) {
                                onAddContact(contactName, contactPhone)
                                contactName = ""
                                contactPhone = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text("TAMBAH", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (favoriteContacts.isEmpty()) {
                    Text(
                        text = "Belum ada kontak terdaftar.",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                } else {
                    favoriteContacts.forEach { (name, phone) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = name.replaceFirstChar { it.uppercase() },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "+$phone",
                                    color = neonGreen,
                                    fontSize = 11.sp
                                )
                            }
                            Button(
                                onClick = { onRemoveContact(name) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C1F26)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Hapus", color = neonRed, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E283A))
                    }
                }
            }
        }

        // 7. Recent Activities Log Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aktivitas Terbaru",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 8.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.logs) { log ->
                            Text(
                                text = log,
                                color = if (log.contains("WhatsApp") || log.contains("pesan") || log.contains("Kontak") || log.contains("balas")) neonGreen else Color.LightGray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color(0xFF00F5A0),
    checkedTrackColor = Color(0xFF00F5A0).copy(alpha = 0.5f),
    uncheckedThumbColor = Color.Gray,
    uncheckedTrackColor = Color.DarkGray
)

private fun twistAnimation(isListening: Boolean): TweenSpec<Float> {
    return tween(durationMillis = if (isListening) 1000 else 1500, easing = LinearEasing)
}

@Composable
fun MicrophoneIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val micW = w * 0.35f
        val micH = h * 0.52f
        val micX = (w - micW) / 2
        val micY = h * 0.12f
        drawRoundRect(
            color = color,
            topLeft = Offset(micX, micY),
            size = Size(micW, micH),
            cornerRadius = CornerRadius(micW / 2, micW / 2)
        )

        val standRadius = w * 0.28f
        val strokeW = w * 0.07f
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset((w - standRadius * 2) / 2, micY + micH - standRadius),
            size = Size(standRadius * 2, standRadius * 2),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        val baseLineY = micY + micH + standRadius
        drawLine(
            color = color,
            start = Offset(w / 2, micY + micH),
            end = Offset(w / 2, baseLineY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        val baseW = w * 0.32f
        drawLine(
            color = color,
            start = Offset((w - baseW) / 2, baseLineY),
            end = Offset((w + baseW) / 2, baseLineY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val packageName = context.packageName
    return enabledListeners != null && enabledListeners.contains(packageName)
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = android.content.ComponentName(context, WhatsAppAccessibilityService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    
    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}
