package com.kairo.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.local.LocalTaskDatabase
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskAlarmLog
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoErrorContainer
import com.kairo.app.ui.theme.KairoOnErrorContainer
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsBottomSheet(
    task: Task,
    onDismiss: () -> Unit,
    onEditClicked: () -> Unit,
    onReactivateForToday: (() -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val dateTimeFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    val createdDateFormatted = remember(task.createdAt) {
        dateTimeFormat.format(Date(task.createdAt))
    }
    val updatedDateFormatted = remember(task.updatedAt) {
        dateTimeFormat.format(Date(task.updatedAt))
    }
    val completedDateFormatted = remember(task.completedAt) {
        task.completedAt?.let { dateTimeFormat.format(Date(it)) }
    }

    val alarmLogs = remember(task.id) {
        LocalTaskDatabase.getInstance(context).getAlarmLogsForTask(task.id)
    }
    val hasAlarmRang = remember(alarmLogs) {
        alarmLogs.any { it.action.equals("RING", ignoreCase = true) }
    }

    val loadedImageBitmap = rememberImageBitmap(context, task.attachmentUri)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KairoSurfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(KairoOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Title & Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditClicked) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Task",
                            tint = KairoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFC8C4D9),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badges row: Status & Priority
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status badge
                if (task.isCompleted) {
                    DetailChip(
                        icon = Icons.Default.CheckCircle,
                        text = "Completed",
                        containerColor = KairoSecondary.copy(alpha = 0.15f),
                        contentColor = KairoSecondary
                    )
                } else {
                    DetailChip(
                        icon = Icons.Default.RadioButtonUnchecked,
                        text = "Active",
                        containerColor = KairoPrimary.copy(alpha = 0.15f),
                        contentColor = KairoPrimary
                    )
                }

                // Priority badge
                val priorityBg = when (task.priority) {
                    Priority.LOW -> KairoSecondary.copy(alpha = 0.15f)
                    Priority.MEDIUM -> KairoPrimary.copy(alpha = 0.15f)
                    Priority.HIGH -> Color(0xFFFFB77D).copy(alpha = 0.18f)
                    Priority.URGENT -> KairoErrorContainer
                }
                val priorityText = when (task.priority) {
                    Priority.LOW -> KairoSecondary
                    Priority.MEDIUM -> KairoPrimary
                    Priority.HIGH -> Color(0xFFFFB77D)
                    Priority.URGENT -> KairoOnErrorContainer
                }
                DetailChip(
                    text = "${task.priority.label} Priority",
                    containerColor = priorityBg,
                    contentColor = priorityText
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Task Title
            Text(
                text = task.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 26.sp
            )

            // Task Notes (if present)
            if (!task.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = KairoSurfaceContainerHighest.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF918EA2),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = task.notes,
                            fontSize = 14.sp,
                            color = Color(0xFFC8C4D9),
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Scheduling & Metadata Details
            Text(
                text = "SCHEDULING & LOCATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF918EA2),
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Due Date & Time
                    val formattedDate = com.kairo.app.ui.utils.DateUtils.formatDisplayDate(task.dueDate, task.dueDateMillis)
                    val dueString = if (!task.dueTime.isNullOrBlank()) {
                        "$formattedDate at ${task.dueTime}"
                    } else {
                        formattedDate
                    }
                    InfoRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Due Date",
                        value = dueString
                    )

                    // Next Alarm Countdown
                    val targetAlarmMillis = com.kairo.app.ui.utils.DateUtils.getTaskAlarmTargetMillis(task)
                    if (targetAlarmMillis != null && !task.isCompleted) {
                        val remainingCountdown = com.kairo.app.ui.utils.DateUtils.formatRemainingTime(targetAlarmMillis)
                        if (remainingCountdown != null) {
                            InfoRow(
                                icon = Icons.Default.Alarm,
                                label = "Next Alarm",
                                value = "In $remainingCountdown",
                                valueColor = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Reminder Alarm Tone
                    InfoRow(
                        icon = Icons.Default.MusicNote,
                        label = "Alarm Tone",
                        value = task.alarmToneTitle ?: "Default Alarm Tone"
                    )

                    // Location
                    if (!task.location.isNullOrBlank()) {
                        InfoRow(
                            icon = Icons.Default.Place,
                            label = "Location",
                            value = task.location
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Repeat Nature
            Text(
                text = "REPEAT NATURE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF918EA2),
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val repeatTypeStr = task.repeatType?.trim().orEmpty()
                    val isRepeating = repeatTypeStr.isNotBlank() && !repeatTypeStr.equals("NONE", ignoreCase = true)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = if (isRepeating) KairoPrimary else Color(0xFF918EA2),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pattern",
                                fontSize = 13.sp,
                                color = Color(0xFF918EA2)
                            )
                        }
                        Text(
                            text = when (repeatTypeStr.uppercase(Locale.US)) {
                                "EVERYDAY", "DAILY" -> "Everyday (Daily)"
                                "DAYS" -> "Specific Days of Week"
                                "DATES" -> "Specific Dates"
                                "MONTHLY_DATES" -> "Monthly on Specific Dates"
                                "BOTH", "BOTH_MONTHLY" -> "Weekly & Monthly Pattern"
                                else -> "Does not repeat (One-time)"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRepeating) KairoPrimary else Color.White
                        )
                    }

                    if (isRepeating) {
                        // Detailed days / dates breakdown
                        val daysList = task.repeatDays?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                        if (daysList.isNotEmpty()) {
                            val fullDayNames = mapOf(
                                "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday",
                                "THU" to "Thursday", "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday"
                            )
                            val formattedDays = when {
                                daysList.size == 7 -> "Every day (Mon to Sun)"
                                daysList.size == 5 && !daysList.contains("SAT") && !daysList.contains("SUN") -> "Weekdays (Mon to Fri)"
                                daysList.size == 2 && daysList.contains("SAT") && daysList.contains("SUN") -> "Weekends (Sat & Sun)"
                                else -> daysList.joinToString(", ") { fullDayNames[it.uppercase(Locale.US)] ?: it }
                            }
                            InfoRow(
                                icon = Icons.Default.CalendarToday,
                                label = "Scheduled Days",
                                value = formattedDays
                            )
                        }

                        val datesList = task.repeatDates?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                        if (datesList.isNotEmpty()) {
                            InfoRow(
                                icon = Icons.Default.CalendarToday,
                                label = if (repeatTypeStr.contains("MONTHLY")) "Monthly Dates" else "Selected Dates",
                                value = datesList.joinToString(", ")
                            )
                        }

                        // Next Occurrence
                        val nextOccurrence = com.kairo.app.ui.utils.DateUtils.calculateNextRepeatDueDate(
                            currentDueDateMillis = task.dueDateMillis,
                            currentDueDateStr = task.dueDate,
                            repeatType = task.repeatType,
                            repeatDays = task.repeatDays,
                            repeatDates = task.repeatDates
                        )
                        if (nextOccurrence != null) {
                            val nextTimeStr = if (!task.dueTime.isNullOrBlank()) " at ${task.dueTime}" else ""
                            InfoRow(
                                icon = Icons.Default.Schedule,
                                label = "Next Recurrence",
                                value = "${nextOccurrence.first}$nextTimeStr",
                                valueColor = Color(0xFF38BDF8)
                            )
                        }

                        // Recurrence behavior explanation
                        Text(
                            text = "• When marked completed, this task automatically rolls over to the next scheduled date.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        val isUpcomingRecurring = !task.repeatType.isNullOrBlank() &&
                            !task.repeatType.equals("NONE", ignoreCase = true) &&
                            task.section == com.kairo.app.data.model.TaskSection.UPCOMING
                        if (isUpcomingRecurring && onReactivateForToday != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Prematurely completed for today?",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Text(
                                            text = "Reactivate to bring this task back to Today and re-arm today's alarm.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFCBD5E1),
                                            lineHeight = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            onReactivateForToday()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0284C7),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Reactivate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "• This is a single one-time task. It will not re-occur after completion.",
                            fontSize = 11.sp,
                            color = Color(0xFF918EA2),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Alarm Activity & History Log
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALARM ACTIVITY LOG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF918EA2),
                    letterSpacing = 0.8.sp
                )
                if (alarmLogs.isNotEmpty()) {
                    Text(
                        text = "${alarmLogs.size} recorded",
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!hasAlarmRang && alarmLogs.isEmpty()) {
                    // Not ringed yet state
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF918EA2).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color(0xFF918EA2),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Not ringed yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val targetMillis = com.kairo.app.ui.utils.DateUtils.getTaskAlarmTargetMillis(task)
                            val countdown = targetMillis?.let { com.kairo.app.ui.utils.DateUtils.formatRemainingTime(it) }
                            val subtext = if (countdown != null) {
                                "Alarm is scheduled to ring in $countdown"
                            } else {
                                "The alarm for this task has not triggered yet."
                            }
                            Text(
                                text = subtext,
                                fontSize = 12.sp,
                                color = Color(0xFF918EA2)
                            )
                        }
                    }
                } else {
                    // Chronological audit log of all events
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!hasAlarmRang) {
                            // Edge case: task was marked completed manually before alarm rang
                            Row(
                                modifier = Modifier.padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = Color(0xFF918EA2),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Alarm has not ringed yet",
                                    fontSize = 12.sp,
                                    color = Color(0xFF918EA2),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }

                        alarmLogs.forEachIndexed { index, log ->
                            val icon = when (log.action.uppercase(Locale.US)) {
                                "RING" -> Icons.Default.Alarm
                                "COMPLETED" -> Icons.Default.CheckCircle
                                "SNOOZED" -> Icons.Default.Snooze
                                "DISMISSED" -> Icons.Default.Close
                                "POSTPONED" -> Icons.Default.Schedule
                                else -> Icons.Default.History
                            }
                            val actionColor = when (log.action.uppercase(Locale.US)) {
                                "RING" -> Color(0xFFF59E0B) // Amber
                                "COMPLETED" -> Color(0xFF10B981) // Emerald Green
                                "SNOOZED" -> Color(0xFFFFB77D) // Warm Orange
                                "DISMISSED" -> Color(0xFFEF4444) // Red
                                "POSTPONED" -> Color(0xFF38BDF8) // Sky Blue
                                else -> Color(0xFF818CF8) // Indigo
                            }
                            val actionLabel = when (log.action.uppercase(Locale.US)) {
                                "RING" -> "Alarm Rang"
                                "COMPLETED" -> "Completed"
                                "SNOOZED" -> "Snoozed"
                                "DISMISSED" -> "Dismissed (Overdue)"
                                "POSTPONED" -> "Postponed"
                                "RESTORED" -> "Marked Active"
                                else -> log.action
                            }
                            val logTimeFormatted = remember(log.timestamp) {
                                dateTimeFormat.format(Date(log.timestamp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(28.dp)
                                        .background(actionColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = actionColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = actionLabel,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = actionColor
                                        )
                                        Text(
                                            text = logTimeFormatted,
                                            fontSize = 11.sp,
                                            color = Color(0xFF918EA2)
                                        )
                                    }
                                    if (!log.details.isNullOrBlank()) {
                                        Text(
                                            text = log.details,
                                            fontSize = 12.sp,
                                            color = Color(0xFFC8C4D9),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (index < alarmLogs.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 14.dp)
                                        .width(1.dp)
                                        .height(8.dp)
                                        .background(KairoOutlineVariant.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: History / Timestamps
            Text(
                text = "TIMESTAMPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF918EA2),
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "Created Time",
                        value = createdDateFormatted
                    )

                    InfoRow(
                        icon = Icons.Default.History,
                        label = "Latest Updated",
                        value = updatedDateFormatted
                    )

                    if (completedDateFormatted != null) {
                        InfoRow(
                            icon = Icons.Default.CheckCircle,
                            label = "Completed Time",
                            value = completedDateFormatted,
                            valueColor = KairoSecondary
                        )
                    }
                }
            }

            // Section: Attachment (Actual visual preview if image, or clean document card)
            if (!task.attachmentName.isNullOrBlank() || !task.attachmentUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "ATTACHMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF918EA2),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (loadedImageBitmap != null) {
                    // Actual photo image preview
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Image(
                                bitmap = loadedImageBitmap,
                                contentDescription = "Attached photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = task.attachmentName ?: "Photo attachment",
                                    fontSize = 12.sp,
                                    color = Color(0xFFC8C4D9),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    // Document / File Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(KairoPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = task.attachmentName ?: "Attached document",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "File attachment",
                                    fontSize = 11.sp,
                                    color = Color(0xFF918EA2)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            val isUpcomingRecurring = !task.repeatType.isNullOrBlank() &&
                !task.repeatType.equals("NONE", ignoreCase = true) &&
                task.section == com.kairo.app.data.model.TaskSection.UPCOMING
            if (isUpcomingRecurring && onReactivateForToday != null) {
                Button(
                    onClick = {
                        onReactivateForToday()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reactivate for Today",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Bottom Action: Edit Task Button
            Button(
                onClick = onEditClicked,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoPrimaryContainer,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Task",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF918EA2),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color(0xFF918EA2)
            )
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun DetailChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
private fun rememberImageBitmap(context: Context, uriString: String?): ImageBitmap? {
    return remember(uriString) {
        if (uriString.isNullOrBlank()) null
        else {
            runCatching {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "content" || uri.scheme == "file") {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } else {
                    val file = File(uriString)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                    } else {
                        val cacheFile = File(context.cacheDir, uriString)
                        if (cacheFile.exists()) {
                            BitmapFactory.decodeFile(cacheFile.absolutePath)?.asImageBitmap()
                        } else null
                    }
                }
            }.getOrNull()
        }
    }
}
