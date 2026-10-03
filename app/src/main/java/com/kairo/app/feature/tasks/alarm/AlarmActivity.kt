package com.kairo.app.feature.tasks.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.alarm.AlarmReceiver
import com.kairo.app.alarm.AlarmRingingService
import com.kairo.app.alarm.AlarmScheduler
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.ui.theme.KAIROTheme
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoErrorContainer
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSecondaryContainer
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class AlarmActivity : ComponentActivity() {

    companion object {
        const val ACTION_FINISH_ALARM_OVERLAY = "com.kairo.app.ACTION_FINISH_ALARM_OVERLAY"
    }

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAndShowOnLock()
        enableEdgeToEdge()

        val filter = IntentFilter(ACTION_FINISH_ALARM_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(finishReceiver, filter)
        }

        val taskId = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_ID).orEmpty()
        val title = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_TITLE).orEmpty().ifBlank { "Client Proposal Review & Budget Sign-off" }
        val notes = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_NOTES)
        val priority = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_PRIORITY).orEmpty().ifBlank { "HIGH" }
        val dueDate = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_DUE_DATE).orEmpty()
        val dueTime = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_DUE_TIME).orEmpty()
        val location = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_LOCATION)
        val attachment = intent.getStringExtra(AlarmRingingService.EXTRA_TASK_ATTACHMENT)

        setContent {
            KAIROTheme {
                AlarmTriggerScreen(
                    taskId = taskId,
                    title = title,
                    notes = notes,
                    priority = priority,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    location = location,
                    attachment = attachment,
                    onComplete = {
                        AlarmRingingService.stop(this@AlarmActivity)
                        if (taskId.isNotBlank()) {
                            AlarmScheduler.cancelAlarm(this@AlarmActivity, taskId)
                            TaskRepository.markTaskCompleted(taskId)
                        }
                        val completeIntent = Intent(this@AlarmActivity, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_COMPLETE_ALARM
                            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                        }
                        sendBroadcast(completeIntent)
                        android.widget.Toast.makeText(this@AlarmActivity, "Task marked as completed", android.widget.Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onSnooze = { minutes ->
                        AlarmRingingService.stop(this@AlarmActivity)
                        val snoozeMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        if (taskId.isNotBlank()) {
                            TaskRepository.snoozeTask(taskId, snoozeMillis)
                        }
                        val snoozeIntent = Intent(this@AlarmActivity, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_SNOOZE_ALARM
                            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, minutes)
                            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, title)
                            putExtra(AlarmReceiver.EXTRA_TASK_NOTES, notes)
                            putExtra(AlarmReceiver.EXTRA_TASK_PRIORITY, priority)
                            putExtra(AlarmReceiver.EXTRA_TASK_DUE_DATE, dueDate)
                            putExtra(AlarmReceiver.EXTRA_TASK_DUE_TIME, dueTime)
                            putExtra(AlarmReceiver.EXTRA_TASK_LOCATION, location)
                            putExtra(AlarmReceiver.EXTRA_TASK_ATTACHMENT, attachment)
                            putExtra(AlarmReceiver.EXTRA_ALARM_URI, intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_URI))
                        }
                        sendBroadcast(snoozeIntent)
                        android.widget.Toast.makeText(this@AlarmActivity, "Alarm snoozed for $minutes minutes", android.widget.Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDismiss = {
                        AlarmRingingService.stop(this@AlarmActivity)
                        if (taskId.isNotBlank()) {
                            AlarmScheduler.cancelAlarm(this@AlarmActivity, taskId)
                            TaskRepository.markTaskOverdue(taskId)
                        }
                        val dismissIntent = Intent(this@AlarmActivity, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_DISMISS_ALARM
                            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                        }
                        sendBroadcast(dismissIntent)
                        android.widget.Toast.makeText(this@AlarmActivity, "Alarm dismissed • Task moved to Overdue", android.widget.Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCloseHeader = {
                        AlarmRingingService.stop(this@AlarmActivity)
                        if (taskId.isNotBlank()) {
                            AlarmScheduler.cancelAlarm(this@AlarmActivity, taskId)
                            TaskRepository.markTaskOverdue(taskId)
                        }
                        val dismissIntent = Intent(this@AlarmActivity, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_DISMISS_ALARM
                            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                        }
                        sendBroadcast(dismissIntent)
                        android.widget.Toast.makeText(this@AlarmActivity, "Alarm dismissed • Task moved to Overdue", android.widget.Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun wakeAndShowOnLock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        AlarmRingingService.stop(this)
        super.onDestroy()
        try {
            unregisterReceiver(finishReceiver)
        } catch (_: Exception) {}
    }
}

@Composable
fun AlarmTriggerScreen(
    taskId: String,
    title: String,
    notes: String?,
    priority: String,
    dueDate: String,
    dueTime: String,
    location: String?,
    attachment: String?,
    onComplete: () -> Unit,
    onSnooze: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
    onCloseHeader: () -> Unit
) {
    androidx.activity.compose.BackHandler {
        onDismiss()
    }

    var showSnoozeDialog by remember { mutableStateOf(false) }
    var currentTimeStr by remember { mutableStateOf(SimpleDateFormat("hh:mm", Locale.getDefault()).format(Date())) }
    var currentAmPm by remember { mutableStateOf(SimpleDateFormat("a", Locale.getDefault()).format(Date())) }
    var currentDateStr by remember { mutableStateOf(SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())) }

    // Live Clock Ticker
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTimeStr = SimpleDateFormat("hh:mm", Locale.getDefault()).format(now)
            currentAmPm = SimpleDateFormat("a", Locale.getDefault()).format(now)
            currentDateStr = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now)
            delay(1000)
        }
    }

    // Ping dot animation
    val pingScale = remember { Animatable(1f) }
    val pingAlpha = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        while (true) {
            pingScale.animateTo(1.7f, animationSpec = tween(900, easing = FastOutSlowInEasing))
            pingScale.snapTo(1f)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            pingAlpha.animateTo(0f, animationSpec = tween(900, easing = FastOutSlowInEasing))
            pingAlpha.snapTo(0.8f)
        }
    }

    // Bell bounce animation
    val bellScale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            bellScale.animateTo(1.2f, animationSpec = tween(350, easing = FastOutSlowInEasing))
            bellScale.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
            delay(400)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KairoBackground)
    ) {
        // 1. Ambient Glow Effects
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .size(240.dp)
                .background(KairoPrimaryContainer.copy(alpha = 0.22f), CircleShape)
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 180.dp)
                .size(160.dp)
                .background(KairoSecondaryContainer.copy(alpha = 0.16f), CircleShape)
                .blur(60.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Close (X) & Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCloseHeader) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Alarm Trigger Overlay",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(KairoPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        fontWeight = FontWeight.Bold,
                        color = KairoPrimaryContainer,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Smart Alarm Badge with Pulsing Indicator
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = KairoSurfaceContainerHigh.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(KairoPrimaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = "KAIRO • SMART ALARM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )

                    // Ping dot
                    Box(modifier = Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(pingScale.value)
                                .background(KairoSecondary.copy(alpha = pingAlpha.value), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(KairoSecondary, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Hero Digital Clock Display
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = currentTimeStr,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = currentAmPm,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = KairoPrimary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFFFFB77D),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Scheduled Task Alert • $currentDateStr",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFC8C4D9)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Central Task Details Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = KairoSurfaceContainerLow,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Top gradient highlight
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, KairoPrimary.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Priority Tag & Bouncing Ringing Bell
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = KairoErrorContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, KairoError.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(KairoError, CircleShape)
                                )
                                Text(
                                    text = "${priority.uppercase()} PRIORITY DEADLINE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KairoError,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .scale(bellScale.value)
                                .background(KairoSurfaceContainerHigh, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Active Alarm",
                                tint = KairoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Project Context Badge / Due time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        val contextSubtitle = if (dueTime.isNotBlank()) "Task Due at $dueTime" else "Priority Focus Task"
                        Text(
                            text = contextSubtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFC8C4D9)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Task Headline
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 26.sp
                    )

                    // Task Description / Notes
                    val noteText = notes ?: "Alarm notification triggered for your scheduled task. Take action or snooze."
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = noteText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF918EA2),
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Soundwave Equalizer Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Ringtone: KAIRO Alarm (Loud)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFC8C4D9)
                            )
                        }

                        // Animated Jumping Audio Wave Bars
                        SoundwaveEqualizer()
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Action Thumb-Zone Floating Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Primary CTA: Mark as Completed
                Button(
                    onClick = onComplete,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KairoSecondary,
                        contentColor = Color(0xFF003825)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Mark as Completed",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Secondary Row: Snooze (Interactive popup) & Dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Snooze Button
                    Button(
                        onClick = { showSnoozeDialog = true },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KairoSurfaceContainerHigh,
                            contentColor = Color(0xFFFFB77D)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Snooze...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Dismiss Button (directly marks task as Overdue)
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KairoSurfaceContainer,
                            contentColor = Color(0xFFC8C4D9)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Dismiss",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Tactile feedback hint
                Text(
                    text = "Tap Mark as Completed, Snooze, or Dismiss to silence",
                    fontSize = 11.sp,
                    color = Color(0xFF6B687C),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                )
            }
        }

        // Interactive Snooze Duration Selector Modal
        if (showSnoozeDialog) {
            AlertDialog(
                onDismissRequest = { showSnoozeDialog = false },
                containerColor = KairoSurfaceContainerHigh,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            tint = Color(0xFFFFB77D),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Snooze Alarm",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Choose how long to snooze before ringing again:",
                            fontSize = 13.sp,
                            color = Color(0xFFC8C4D9),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val snoozeOptions = listOf(2, 5, 10, 15, 30)
                        snoozeOptions.forEach { minutes ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSnoozeDialog = false
                                        onSnooze(minutes)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = KairoSurfaceContainerHighest.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$minutes minutes",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB77D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSnoozeDialog = false }) {
                        Text("Cancel", color = Color(0xFFC8C4D9))
                    }
                }
            )
        }
    }
}

@Composable
private fun SoundwaveEqualizer() {
    val bar1 = remember { Animatable(8f) }
    val bar2 = remember { Animatable(16f) }
    val bar3 = remember { Animatable(12f) }
    val bar4 = remember { Animatable(18f) }
    val bar5 = remember { Animatable(6f) }

    LaunchedEffect(Unit) {
        while (true) {
            bar1.animateTo(16f, tween(300, easing = LinearEasing))
            bar1.animateTo(6f, tween(300, easing = LinearEasing))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            bar2.animateTo(8f, tween(240, easing = LinearEasing))
            bar2.animateTo(18f, tween(240, easing = LinearEasing))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            bar3.animateTo(18f, tween(360, easing = LinearEasing))
            bar3.animateTo(10f, tween(360, easing = LinearEasing))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            bar4.animateTo(6f, tween(280, easing = LinearEasing))
            bar4.animateTo(16f, tween(280, easing = LinearEasing))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            bar5.animateTo(14f, tween(320, easing = LinearEasing))
            bar5.animateTo(4f, tween(320, easing = LinearEasing))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(20.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(bar1.value.dp).background(KairoPrimary, RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.width(3.dp).height(bar2.value.dp).background(KairoSecondary, RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.width(3.dp).height(bar3.value.dp).background(Color(0xFFFFB77D), RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.width(3.dp).height(bar4.value.dp).background(KairoPrimary, RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.width(3.dp).height(bar5.value.dp).background(KairoSecondary, RoundedCornerShape(2.dp)))
    }
}
