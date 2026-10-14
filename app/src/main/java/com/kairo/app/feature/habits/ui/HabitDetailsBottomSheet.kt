package com.kairo.app.feature.habits.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailsBottomSheet(
    habitWithStats: HabitWithStats,
    onDismiss: () -> Unit,
    onEditClicked: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val habit = habitWithStats.habit
    val today = remember { LocalDate.now() }
    val effectiveStartDate = remember(habit.startDate, habit.createdAt) {
        HabitRepository.getEffectiveStartDate(habit)
    }

    val dateTimeFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    val createdDateFormatted = remember(habit.startDate, habit.createdAt) {
        if (habit.startDate.isNotBlank()) {
            runCatching {
                val parsed = LocalDate.parse(habit.startDate, DateTimeFormatter.ISO_LOCAL_DATE)
                parsed.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            }.getOrElse { dateFormat.format(Date(habit.createdAt)) }
        } else {
            dateFormat.format(Date(habit.createdAt))
        }
    }

    val updatedDateFormatted = remember(habit.updatedAt) {
        dateTimeFormat.format(Date(habit.updatedAt))
    }

    val isTodayRest = habit.repeatType == HabitRepeatType.SPECIFIC_DAYS && !habit.repeatDays.contains(today.dayOfWeek.value)
    val isBeforeCreation = today.isBefore(effectiveStartDate)
    val isTodayDone = habitWithStats.todayProgressFraction >= 1f ||
            habitWithStats.days.any { it.isToday && it.state == HabitDayState.DONE }

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
                .padding(bottom = 28.dp)
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
                    text = "Habit Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditClicked) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Habit",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Badges row: Status & Type
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Today's completion status chip
                when {
                    isBeforeCreation -> {
                        HabitDetailChip(
                            icon = Icons.Default.CalendarToday,
                            text = "Starts ${effectiveStartDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color(0xFF94A3B8)
                        )
                    }
                    isTodayRest -> {
                        HabitDetailChip(
                            icon = Icons.Default.EventBusy,
                            text = "Rest Day",
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color(0xFF94A3B8)
                        )
                    }
                    isTodayDone -> {
                        HabitDetailChip(
                            icon = Icons.Default.CheckCircle,
                            text = "Completed Today",
                            containerColor = KairoSecondary.copy(alpha = 0.18f),
                            contentColor = KairoSecondary
                        )
                    }
                    else -> {
                        HabitDetailChip(
                            icon = Icons.Default.RadioButtonUnchecked,
                            text = "Pending Today",
                            containerColor = Color(0xFFFACC15).copy(alpha = 0.15f),
                            contentColor = Color(0xFFFACC15)
                        )
                    }
                }

                // Type chip
                val typeLabel = if (habit.type == HabitType.TARGET) "Target: ${habit.targetValue} ${habit.targetUnit}" else "Yes / No"
                HabitDetailChip(
                    icon = Icons.Default.TrackChanges,
                    text = typeLabel,
                    containerColor = KairoPrimary.copy(alpha = 0.15f),
                    contentColor = KairoPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Habit Title
            Text(
                text = habit.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Schedule & Lifecycle Information
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KairoSurfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "SCHEDULE & SETUP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )

                    HabitInfoRow(
                        icon = Icons.Default.CalendarMonth,
                        label = "Created Date",
                        value = createdDateFormatted
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    HabitInfoRow(
                        icon = Icons.Default.Update,
                        label = "Latest Updated",
                        value = updatedDateFormatted
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    val repeatLabel = when (habit.repeatType) {
                        HabitRepeatType.EVERYDAY -> "Everyday"
                        HabitRepeatType.SPECIFIC_DAYS -> {
                            val dayMap = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
                            "Specific Days (${habit.repeatDays.sorted().mapNotNull { dayMap[it] }.joinToString(", ")})"
                        }
                        HabitRepeatType.WEEKLY_X -> "Weekly (${habit.weeklyTargetCount}x / week)"
                    }
                    HabitInfoRow(
                        icon = Icons.Default.Repeat,
                        label = "Repeat Frequency",
                        value = repeatLabel
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    HabitInfoRow(
                        icon = Icons.Default.Description,
                        label = "Notes Format",
                        value = habit.notePrompt.name
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 2: Today's Status Details
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KairoSurfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "TODAY'S STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )

                    val statusText = when {
                        isBeforeCreation -> "Not started yet (Starts ${effectiveStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE)})"
                        isTodayRest -> "Rest Day"
                        isTodayDone -> "Completed"
                        habit.type == HabitType.TARGET && habitWithStats.todayValue > 0 -> "In Progress (${habitWithStats.todayValue}/${habit.targetValue} ${habit.targetUnit})"
                        else -> "Pending"
                    }
                    val statusColor = when {
                        isBeforeCreation -> Color(0xFF94A3B8)
                        isTodayRest -> Color(0xFF94A3B8)
                        isTodayDone -> KairoSecondary
                        habit.type == HabitType.TARGET && habitWithStats.todayValue > 0 -> Color(0xFFFACC15)
                        else -> Color(0xFFFFB77D)
                    }

                    HabitInfoRow(
                        icon = if (isTodayDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        label = "Completion Status",
                        value = statusText,
                        valueColor = statusColor
                    )

                    if (habit.type == HabitType.TARGET) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        HabitInfoRow(
                            icon = Icons.Default.TrackChanges,
                            label = "Target Progress",
                            value = "${habitWithStats.todayValue} / ${habit.targetValue} ${habit.targetUnit} (${(habitWithStats.todayProgressFraction * 100).toInt()}%)"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 3: Reminder Setup
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KairoSurfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "REMINDERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )

                    HabitInfoRow(
                        icon = Icons.Default.NotificationsActive,
                        label = "Reminder Alert",
                        value = if (habit.reminderEnabled) "Enabled (${habit.reminderTime ?: "Time set"})" else "Disabled",
                        valueColor = if (habit.reminderEnabled) KairoPrimary else Color(0xFF94A3B8)
                    )

                    if (habit.reminderEnabled && !habit.reminderToneTitle.isNullOrBlank()) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        HabitInfoRow(
                            icon = Icons.Default.Alarm,
                            label = "Ringtone",
                            value = habit.reminderToneTitle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 4: Performance & Analytics
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KairoSurfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ANALYTICS & METRICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )

                    val streakUnit = when (habit.repeatType) {
                        HabitRepeatType.WEEKLY_X -> "Weeks"
                        HabitRepeatType.SPECIFIC_DAYS -> "Sessions"
                        HabitRepeatType.EVERYDAY -> "Days"
                    }

                    HabitInfoRow(
                        icon = Icons.Default.LocalFireDepartment,
                        label = "Current Streak",
                        value = "${habitWithStats.streak} $streakUnit",
                        valueColor = Color(0xFFFCD34D)
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    HabitInfoRow(
                        icon = Icons.Default.LocalFireDepartment,
                        label = "Best Streak",
                        value = "${habitWithStats.bestStreak} $streakUnit",
                        valueColor = Color(0xFFFCD34D)
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    HabitInfoRow(
                        icon = Icons.Default.CheckCircle,
                        label = "Total Completions",
                        value = "${habitWithStats.doneCount} times",
                        valueColor = KairoSecondary
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    HabitInfoRow(
                        icon = Icons.Default.Speed,
                        label = "Adherence Rate",
                        value = "${(habitWithStats.adherenceRate * 100).toInt()}%"
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitInfoRow(
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
private fun HabitDetailChip(
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
