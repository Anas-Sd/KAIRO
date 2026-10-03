package com.kairo.app.feature.ai

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoCardSurface
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import kotlinx.coroutines.launch
import org.json.JSONObject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val providerUsed: String? = null,
    val latencyMs: Long? = null,
    val quotaWarning: String? = null,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val pendingActionJson: String? = null,
    val isConfirmed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun AiAssistantScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val isListening by AiVoiceManager.isListening.collectAsState()
    val isSpeaking by AiVoiceManager.isSpeaking.collectAsState()
    var isVoiceModeActive by remember { mutableStateOf(AiVoiceManager.isVoiceModeActive) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "Hey Buddy! I'm your KAIRO personal task executive. I can create, organize, group, snooze, or query your tasks. You can speak to me or send photos of lists.",
                isUser = false,
                providerUsed = "KAIRO Executive Core"
            )
        )
    }
    val listState = rememberLazyListState()

    // Pulse animation for active voice mode
    val micPulse = remember { Animatable(1f) }
    LaunchedEffect(isListening) {
        if (isListening) {
            micPulse.animateTo(
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            micPulse.snapTo(1f)
        }
    }

    // Photo picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                attachedBitmap = bitmap
                Toast.makeText(context, "Image attached! Ask KAIRO to extract tasks.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Continuous voice-to-voice conversation loop
    fun startListeningLoop() {
        if (!isVoiceModeActive) return
        AiVoiceManager.startListening(context) { heardSpeech ->
            val clean = heardSpeech.trim()
            if (clean.isNotBlank()) {
                messages.add(ChatMessage(text = clean, isUser = true))
                isLoading = true

                scope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                    val history = messages.map { it.text to it.isUser }
                    val response = AiEngine.chat(clean, null, history)
                    isLoading = false

                    messages.add(
                        ChatMessage(
                            text = response.text,
                            isUser = false,
                            providerUsed = response.providerUsed,
                            latencyMs = response.latencyMs,
                            quotaWarning = response.quotaWarning,
                            requiresConfirmation = response.requiresConfirmation,
                            confirmationPrompt = response.confirmationPrompt,
                            pendingActionJson = response.pendingActionJson
                        )
                    )
                    listState.animateScrollToItem(messages.size - 1)

                    // Speak aloud with zero delay, then automatically resume listening
                    AiVoiceManager.speak(response.text) {
                        if (isVoiceModeActive) {
                            startListeningLoop()
                        }
                    }
                }
            }
        }
    }

    // Toggle Voice Mode
    fun toggleVoiceMode() {
        if (isVoiceModeActive) {
            isVoiceModeActive = false
            AiVoiceManager.setVoiceMode(false)
        } else {
            isVoiceModeActive = true
            AiVoiceManager.setVoiceMode(true)
            startListeningLoop()
        }
    }

    // Text Message Send Handler
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachedBitmap == null) return
        if (isLoading) return

        val userPrompt = trimmed.ifEmpty { "Extract all items from this image into my tasks." }
        messages.add(ChatMessage(text = userPrompt, isUser = true))
        inputText = ""
        val imageToProcess = attachedBitmap
        attachedBitmap = null
        isLoading = true

        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val history = messages.map { it.text to it.isUser }
            val response = AiEngine.chat(userPrompt, imageToProcess, history)
            isLoading = false

            messages.add(
                ChatMessage(
                    text = response.text,
                    isUser = false,
                    providerUsed = response.providerUsed,
                    latencyMs = response.latencyMs,
                    quotaWarning = response.quotaWarning,
                    requiresConfirmation = response.requiresConfirmation,
                    confirmationPrompt = response.confirmationPrompt,
                    pendingActionJson = response.pendingActionJson
                )
            )
            listState.animateScrollToItem(messages.size - 1)

            if (isVoiceModeActive) {
                AiVoiceManager.speak(response.text) {
                    if (isVoiceModeActive) startListeningLoop()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AiVoiceManager.setVoiceMode(false)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = KairoSurfaceContainer,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KairoPrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "KAIRO Executive AI",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isVoiceModeActive) {
                                if (isListening) "🎙️ Listening to you..." else if (isSpeaking) "🔊 Speaking..." else "Voice mode ready"
                            } else {
                                "Groq 120B • Gemini Vision • Offline Fallback"
                            },
                            fontSize = 11.sp,
                            color = if (isVoiceModeActive) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Voice Mode Toggle Button
                    IconButton(
                        onClick = { toggleVoiceMode() },
                        modifier = Modifier
                            .scale(if (isVoiceModeActive) micPulse.value else 1f)
                            .background(
                                if (isVoiceModeActive) KairoPrimary else Color.Transparent,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isVoiceModeActive) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Toggle Voice Mode",
                            tint = if (isVoiceModeActive) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        containerColor = KairoBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onConfirmAction = { actionJson ->
                            try {
                                val actionObj = JSONObject(actionJson)
                                val toolName = if (actionObj.has("taskIds")) "batch_delete_tasks" else "delete_task"
                                val result = AiToolExecutor.execute(toolName, actionObj)
                                val idx = messages.indexOfFirst { it.id == message.id }
                                if (idx >= 0) {
                                    messages[idx] = message.copy(isConfirmed = true)
                                }
                                messages.add(
                                    ChatMessage(
                                        text = "✓ Action Confirmed: ${result.message}",
                                        isUser = false,
                                        providerUsed = "KAIRO Execution Core"
                                    )
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to execute: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCancelAction = {
                            val idx = messages.indexOfFirst { it.id == message.id }
                            if (idx >= 0) {
                                messages[idx] = message.copy(isConfirmed = true)
                            }
                            messages.add(
                                ChatMessage(
                                    text = "Cancelled action.",
                                    isUser = false,
                                    providerUsed = "KAIRO Execution Core"
                                )
                            )
                        }
                    )
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = KairoPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "KAIRO is thinking...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Attached Image Thumbnail Preview (if picked)
            AnimatedVisibility(visible = attachedBitmap != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .background(KairoCardSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, KairoOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached Image",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Image ready for task extraction",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { attachedBitmap = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Image",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Plan my day", "Tasks status", "Clean completed").forEach { chip ->
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = KairoCardSurface,
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { sendMessage(chip) }
                    ) {
                        Text(
                            text = chip,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = KairoSurfaceContainer,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image attachment icon
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach image",
                            tint = if (attachedBitmap != null) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask KAIRO or dictate tasks...") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KairoPrimary,
                            unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.3f),
                            cursorColor = KairoPrimary
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = (inputText.isNotBlank() || attachedBitmap != null) && !isLoading,
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if ((inputText.isNotBlank() || attachedBitmap != null) && !isLoading) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.25f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if ((inputText.isNotBlank() || attachedBitmap != null) && !isLoading) Color.Black else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onConfirmAction: (String) -> Unit,
    onCancelAction: () -> Unit
) {
    val isUser = message.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(KairoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = KairoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isUser) 14.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 14.dp
                ),
                color = if (isUser) KairoPrimary else KairoCardSurface,
                border = if (!isUser) BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f)) else null,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        fontSize = 13.sp,
                        color = if (isUser) Color.Black else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )

                    // Interactive Confirmation Card for destructive/ambiguous operations
                    if (message.requiresConfirmation && !message.isConfirmed && message.pendingActionJson != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KairoSurfaceContainerHigh,
                            border = BorderStroke(1.dp, KairoError.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = "Warning",
                                        tint = KairoError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = message.confirmationPrompt ?: "Confirmation needed",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onConfirmAction(message.pendingActionJson) },
                                        colors = ButtonDefaults.buttonColors(containerColor = KairoError),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text("Confirm", fontSize = 12.sp, color = Color.White)
                                    }
                                    Button(
                                        onClick = onCancelAction,
                                        colors = ButtonDefaults.buttonColors(containerColor = KairoOutlineVariant.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text("Cancel", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Provider & Latency Badge
        if (!isUser && message.providerUsed != null) {
            Row(
                modifier = Modifier.padding(start = 36.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val latencyText = if (message.latencyMs != null && message.latencyMs > 0) " • ${message.latencyMs}ms" else ""
                Text(
                    text = "⚡ ${message.providerUsed}$latencyText",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                if (message.quotaWarning != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${message.quotaWarning})",
                        fontSize = 10.sp,
                        color = Color(0xFFFFB77D)
                    )
                }
            }
        }
    }
}
