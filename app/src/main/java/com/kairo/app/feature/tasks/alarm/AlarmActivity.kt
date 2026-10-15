package com.kairo.app.feature.tasks.alarm

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
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
        wakeAndShowOnLock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        wakeAndShowOnLock()
        super.onCreate(savedInstanceState)
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
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        wakeAndShowOnLock()
    }

    override fun onResume() {
        super.onResume()
        wakeAndShowOnLock()
    }

    override fun onDestroy() {
        try {
            AlarmOverlayManager.dismiss()
        } catch (_: Exception) {}
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

    val context = androidx.compose.ui.platform.LocalContext.current

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



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 1. Digital Clock & Date
            Text(
                text = "$currentTimeStr $currentAmPm",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentDateStr,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF9CA3AF)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 2. Center Content - Task Title & Details (Scrollable if very long)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 36.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (!notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = notes,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF9CA3AF),
                        lineHeight = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                if (dueTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Due at $dueTime",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7280),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Action Buttons: Completed, Snooze in 5min, Dismiss
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Completed
                Button(
                    onClick = onComplete,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "Completed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Snooze in 5min
                Button(
                    onClick = { onSnooze(5) },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1917),
                        contentColor = Color(0xFFF59E0B)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFF78350F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Snooze in 5min",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. Dismiss
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF171717),
                        contentColor = Color(0xFF9CA3AF)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF262626)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
