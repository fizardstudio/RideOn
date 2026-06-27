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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository.getInstance()) },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Check permission state in UI
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(isNotificationServiceEnabled(context))
    }

    // Permission request launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    // Refresh permissions on resume
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasNotificationPermission = isNotificationServiceEnabled(context)
            }
        }
        // Simplified lifecycle observing
        onDispose {}
    }

    DashboardContent(
        state = state,
        hasMicPermission = hasMicPermission,
        hasNotificationPermission = hasNotificationPermission,
        onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onRequestNotificationPermission = {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        },
        onMicClicked = { viewModel.onMicClicked() },
        onStartService = { viewModel.startService(context) },
        onStopService = { viewModel.stopService(context) },
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    state: MainUiState,
    hasMicPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onMicClicked: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkBackground = Color(0xFF0B0F19)
    val cardBackground = Color(0xFF171E2E)
    val neonCyan = Color(0xFF00F2FE)
    val neonGreen = Color(0xFF00F5A0)
    val neonRed = Color(0xFFFF0844)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title Header
        Text(
            text = "DRIVING ASSISTANT",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Tetap fokus berkendara. Biarkan asisten membantu Anda.",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Permission Banners
        if (!hasMicPermission || !hasNotificationPermission) {
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
                        text = "Aplikasi memerlukan izin Mikrofon & Akses Notifikasi agar dapat membacakan dan membalas pesan secara otomatis.",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (!hasMicPermission) {
                            Button(
                                onClick = onRequestMicPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = neonRed)
                            ) {
                                Text("Izin Mic", fontSize = 11.sp, color = Color.White)
                            }
                        }
                        if (!hasNotificationPermission) {
                            Button(
                                onClick = onRequestNotificationPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = neonRed)
                            ) {
                                Text("Akses Notifikasi", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Service Lifecycle Control Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
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

        // Animated Pulse Microphone Section
        Box(
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val isListening = state.assistantState == AssistantState.LISTENING_COMMAND || state.assistantState == AssistantState.LISTENING_REPLY
            val isSpeaking = state.assistantState == AssistantState.SPEAKING
            
            // Animation for pulse effect
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
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isListening) neonCyan else neonGreen).copy(alpha = alpha)
                        )
                        .align(Alignment.Center)
                )
            }

            // Central Mic Button
            val buttonColor = when (state.assistantState) {
                AssistantState.LISTENING_COMMAND, AssistantState.LISTENING_REPLY -> neonCyan
                AssistantState.SPEAKING -> neonGreen
                AssistantState.IDLE -> Color(0xFF232D42)
            }

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(buttonColor, buttonColor.copy(alpha = 0.8f))))
                    .clickable(enabled = hasMicPermission) { onMicClicked() },
                contentAlignment = Alignment.Center
            ) {
                MicrophoneIcon(
                    modifier = Modifier.size(50.dp),
                    color = if (state.assistantState == AssistantState.IDLE) Color.White else Color.Black
                )
            }
        }

        // Assistant Status Display
        val statusText = when (state.assistantState) {
            AssistantState.IDLE -> "Asisten Siap (Idle)"
            AssistantState.SPEAKING -> "Asisten sedang Berbicara..."
            AssistantState.LISTENING_COMMAND -> "Mendengarkan Perintah..."
            AssistantState.LISTENING_REPLY -> "Mendengarkan Balasan WhatsApp..."
        }
        val statusColor = when (state.assistantState) {
            AssistantState.IDLE -> Color.Gray
            AssistantState.SPEAKING -> neonGreen
            else -> neonCyan
        }

        Text(
            text = statusText,
            color = statusColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Logs and History Section
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Aktivitas Terbaru",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.logs) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("WhatsApp") || log.contains("pesan")) neonGreen else Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// Custom tween provider
private fun twistAnimation(isListening: Boolean): TweenSpec<Float> {
    return tween(durationMillis = if (isListening) 1000 else 1500, easing = LinearEasing)
}

// Custom canvas micro-drawn mic
@Composable
fun MicrophoneIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Microphone core pill
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

        // Microphone outer U-stand
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

        // Center post connecting U-stand to base
        val baseLineY = micY + micH + standRadius
        drawLine(
            color = color,
            start = Offset(w / 2, micY + micH),
            end = Offset(w / 2, baseLineY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Flat base stand at the bottom
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
