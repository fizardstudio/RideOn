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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val darkBackground = Color(0xFF090D16) // Deep midnight black
    val cardBackground = Color(0xFF1E293B) // Slate-800
    val borderStrokeColor = Color(0xFF334155) // Slate-700
    val neonCyan = Color(0xFF06B6D4) // Modern Cyan-500
    val neonBlue = Color(0xFF3B82F6) // Bright blue
    val neonGreen = Color(0xFF10B981) // Emerald Green
    val neonRed = Color(0xFFEF4444)
    val lightGray = Color(0xFF94A3B8)

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title Header with Futuristic Gradient
        Text(
            text = "RIDE ON",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            style = LocalTextStyle.current.copy(
                brush = Brush.horizontalGradient(listOf(neonCyan, Color(0xFF8B5CF6))) // Cyan to Violet gradient
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
        )

        Text(
            text = "Asisten Suara Hands-free Berkendara",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = lightGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        // 1. Live Interactive Visualizer Card (Radar, Wave, and Status)
        PremiumCard(
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive State Badge
                val (statusText, statusLabelColor, badgeBg) = when (state.assistantState) {
                    AssistantState.IDLE -> Triple("SIAGA LAMBAI", lightGray, Color(0xFF334155).copy(alpha = 0.4f))
                    AssistantState.SPEAKING -> Triple("BERBICARA", neonGreen, Color(0xFF064E3B).copy(alpha = 0.5f))
                    AssistantState.LISTENING_COMMAND -> Triple("MENDENGAR PERINTAH", neonCyan, Color(0xFF0891B2).copy(alpha = 0.3f))
                    AssistantState.LISTENING_REPLY -> Triple("MENDENGAR TANGGAPAN", neonCyan, Color(0xFF0891B2).copy(alpha = 0.3f))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Small pulse dot
                        val dotAlpha = remember { Animatable(1f) }
                        if (state.assistantState != AssistantState.IDLE) {
                            LaunchedEffect(Unit) {
                                while (true) {
                                    dotAlpha.animateTo(0.2f, animationSpec = tween(500, easing = LinearEasing))
                                    dotAlpha.animateTo(1f, animationSpec = tween(500, easing = LinearEasing))
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusLabelColor.copy(alpha = dotAlpha.value))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusLabelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Radar Mic Visualizer
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val isListening = state.assistantState == AssistantState.LISTENING_COMMAND || state.assistantState == AssistantState.LISTENING_REPLY
                    val isSpeaking = state.assistantState == AssistantState.SPEAKING

                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = if (isListening) 1.5f else if (isSpeaking) 1.3f else 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(if (isListening) 500 else if (isSpeaking) 900 else 2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 0.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(if (isListening) 500 else if (isSpeaking) 900 else 2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    // Orbit line 1 (static dashed)
                    Canvas(modifier = Modifier.size(130.dp)) {
                        drawCircle(
                            color = Color(0xFF334155).copy(alpha = 0.4f),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            )
                        )
                    }

                    // Orbit line 2 (static dashed larger)
                    Canvas(modifier = Modifier.size(154.dp)) {
                        drawCircle(
                            color = Color(0xFF334155).copy(alpha = 0.2f),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                            )
                        )
                    }

                    // Pulse outer circle
                    Box(
                        modifier = Modifier
                            .size(106.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(
                                (if (isListening) neonCyan else if (isSpeaking) neonGreen else neonBlue).copy(alpha = alpha)
                            )
                    )

                    val centerColor = when {
                        isListening -> neonCyan
                        isSpeaking -> neonGreen
                        else -> Color(0xFF334155)
                    }

                    // Inner visualizer button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        centerColor,
                                        centerColor.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable(enabled = hasMicPermission) { onMicClicked() },
                        contentAlignment = Alignment.Center
                    ) {
                        MicrophoneIcon(
                            modifier = Modifier.size(32.dp),
                            color = if (state.assistantState == AssistantState.IDLE) Color.White else Color(0xFF0F172A)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (state.assistantState == AssistantState.IDLE) "Lambaikan tangan atau ketuk mic untuk bicara" else "Asisten aktif merespons suara Anda",
                    color = lightGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 2. Permission Banners (Mic, Notification, Contacts, Accessibility)
        if (!hasMicPermission || !hasNotificationPermission || !hasContactsPermission || !hasAccessibilityPermission) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1A22)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF6B21A8).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Izin Diperlukan",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aktifkan akses berikut agar asisten WhatsApp berkendara Anda berfungsi 100% otomatis:",
                        color = Color(0xFFFECACA),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!hasMicPermission) {
                                Button(
                                    onClick = onRequestMicPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("Izin Mic", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!hasNotificationPermission) {
                                Button(
                                    onClick = onRequestNotificationPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("Izin Notifikasi", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!hasContactsPermission) {
                                Button(
                                    onClick = onRequestContactsPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("Izin Kontak", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!hasAccessibilityPermission) {
                                Button(
                                    onClick = onRequestAccessibilityPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("Auto-Kirim", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Layanan Control & Quick Tutorial Card (With Settings and Speaker custom drawings)
        PremiumCard(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF334155).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SettingsIcon(modifier = Modifier.size(20.dp), color = neonCyan)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Layanan Latar Belakang", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Deteksi otomatis berkendara aktif", color = lightGray, fontSize = 10.sp)
                    }
                }
                Row {
                    Button(
                        onClick = onStartService,
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text("START", color = Color(0xFF090D16), fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                    Button(
                        onClick = onStopService,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        Text("STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 12.dp))

            // Voice Tutorial Entry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF334155).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SpeakerIcon(modifier = Modifier.size(20.dp), color = neonCyan)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Panduan Suara Asisten", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Putar cara pakai lisan hands-free", color = lightGray, fontSize = 10.sp)
                    }
                }
                Button(
                    onClick = onPlayVoiceTutorial,
                    colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    PlayIcon(modifier = Modifier.size(10.dp), color = Color(0xFF090D16))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PUTAR", color = Color(0xFF090D16), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }

        // 4. Feature Configurations Card (Auto-Read, Driving Mode, Ignore Groups, Speech Rate with icons)
        PremiumCard(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Konfigurasi Fitur",
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            SpeakerIcon(modifier = Modifier.size(16.dp), color = neonCyan)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Auto-Read WhatsApp", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Membacakan pesan masuk otomatis", color = lightGray, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = autoReadEnabled,
                        onCheckedChange = onToggleAutoRead,
                        colors = switchColors()
                    )
                }

                HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Toggle Ignore Group Chats
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ContactsIcon(modifier = Modifier.size(16.dp), color = neonCyan)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Abaikan Chat Grup", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Mendiamkan notifikasi grup chat", color = lightGray, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = ignoreGroupsEnabled,
                        onCheckedChange = onToggleIgnoreGroups,
                        colors = switchColors()
                    )
                }

                HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Toggle Driving Mode (Auto-Reply)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            SettingsIcon(modifier = Modifier.size(16.dp), color = neonCyan)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Mode Menyetir (Auto-Reply)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Kirim template pesan otomatis hening", color = lightGray, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = drivingModeEnabled,
                        onCheckedChange = onToggleDrivingMode,
                        colors = switchColors()
                    )
                }

                HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Speech Rate Slider
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kecepatan Bicara Asisten", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${String.format("%.1f", speechRate)}x", color = neonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            inactiveTrackColor = Color(0xFF334155)
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
                            unfocusedBorderColor = borderStrokeColor,
                            focusedLabelColor = neonCyan,
                            unfocusedLabelColor = lightGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onSaveTemplate(templateText) },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("SIMPAN TEMPLATE", color = Color(0xFF090D16), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
        }

        // 5. Favorite Contacts Manager Card (With gradient avatar bubbles)
        PremiumCard(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            var contactName by remember { mutableStateOf("") }
            var contactPhone by remember { mutableStateOf("") }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF334155).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        ContactsIcon(modifier = Modifier.size(16.dp), color = neonCyan)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Daftar Kontak Favorit",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Panggilan lisan dengan nomor WhatsApp",
                            color = lightGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                            unfocusedBorderColor = borderStrokeColor,
                            focusedLabelColor = neonCyan,
                            unfocusedLabelColor = lightGray,
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
                            unfocusedBorderColor = borderStrokeColor,
                            focusedLabelColor = neonCyan,
                            unfocusedLabelColor = lightGray,
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
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text("TAMBAH", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (favoriteContacts.isEmpty()) {
                    Text(
                        text = "Belum ada kontak terdaftar.",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                } else {
                    favoriteContacts.forEach { (name, phone) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Circular Avatar Bubble with gradient
                                val initial = name.firstOrNull()?.uppercase() ?: "?"
                                val avatarGradient = Brush.linearGradient(
                                    listOf(neonCyan, Color(0xFF8B5CF6)) // Cyan to violet gradient matching app header
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(avatarGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = name.replaceFirstChar { it.uppercase() },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "+$phone",
                                        color = neonCyan,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { onRemoveContact(name) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1F27)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Hapus", color = neonRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.3f), modifier = Modifier.padding(start = 48.dp))
                    }
                }
            }
        }

        // 6. Recent Terminal-like Console Logs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderStrokeColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF020617)) // Terminal dark black
                .height(200.dp)
        ) {
            Column {
                // Fake OS Window top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Mac-like traffic light window controls
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    }
                    
                    // Middle: Tab title
                    Text(
                        text = "bash - Ride On Log Console",
                        color = lightGray,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    
                    // Right: Console indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(neonGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ACTIVE",
                            color = neonGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.3f))

                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.logs) { log ->
                            Text(
                                text = "> $log",
                                color = if (log.contains("WhatsApp") || log.contains("pesan") || log.contains("Kontak") || log.contains("balas")) neonGreen else Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardGradient = Brush.verticalGradient(
        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardGradient, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        
        drawCircle(color = color, radius = w * 0.28f, style = Stroke(width = w * 0.08f))
        drawCircle(color = color, radius = w * 0.10f)
        
        val numTeeth = 8
        val toothWidth = w * 0.06f
        
        for (i in 0 until numTeeth) {
            val angle = i * (360f / numTeeth)
            val rad = Math.toRadians(angle.toDouble())
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            
            val startX = cx + cos * (w * 0.26f)
            val startY = cy + sin * (h * 0.26f)
            val endX = cx + cos * (w * 0.38f)
            val endY = cy + sin * (h * 0.38f)
            
            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = toothWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun ContactsIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        drawCircle(
            color = color,
            radius = w * 0.20f,
            center = Offset(w / 2, h * 0.35f)
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.60f),
            size = Size(w * 0.70f, h * 0.40f),
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun TerminalIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.2f, h * 0.25f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.2f, h * 0.75f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
        
        drawLine(
            color = color,
            start = Offset(w * 0.58f, h * 0.75f),
            end = Offset(w * 0.88f, h * 0.75f),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SpeakerIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.35f, h * 0.35f)
            lineTo(w * 0.60f, h * 0.15f)
            lineTo(w * 0.60f, h * 0.85f)
            lineTo(w * 0.35f, h * 0.65f)
            lineTo(w * 0.15f, h * 0.65f)
            close()
        }
        drawPath(path = path, color = color)
        
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.40f, h * 0.25f),
            size = Size(w * 0.50f, h * 0.50f),
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun PlayIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.30f, h * 0.25f)
            lineTo(w * 0.75f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.75f)
            close()
        }
        drawPath(path = path, color = color)
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
