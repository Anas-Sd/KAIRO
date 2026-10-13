package com.kairo.app.feature.habits.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.model.HabitDayInfo
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import java.time.LocalDate

@Composable
fun HabitCard(
    habitWithStats: HabitWithStats,
    onTitleClick: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onIncrement: () -> Unit = {},
    onDecrement: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    isBeingDragged: Boolean = false,
    dragOffsetY: Float = 0f,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    val scrollState = rememberScrollState()

    // Automatically scroll to today (the rightmost day)
    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairoSurfaceContainer)
            .border(
                1.dp,
                if (isBeingDragged) KairoPrimary else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT COLUMN: Drag Grabbing Icon + Selection Checkbox below it
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(dragHandleModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = if (isBeingDragged) KairoPrimary else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Small Selection Checkbox below the grabbing icon when in select mode
                if (isSelectionMode) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSelected) KairoPrimary else Color.White.copy(alpha = 0.05f))
                            .border(
                                1.dp,
                                if (isSelected) KairoPrimary else Color.White.copy(alpha = 0.35f),
                                RoundedCornerShape(5.dp)
                            )
                            .clickable { onSelectionChange(!isSelected) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // MIDDLE: Task Name + Stepper (for Target) + Underline + Horizontal Swipable Checkbox Strip
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                // Clickable upper area (header + stepper) opens HabitDetailScreen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTitleClick() }
                ) {
                    // Header Row: Task Name + Schedule Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = habit.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Schedule or Weekly Progress Pill
                        when (habit.repeatType) {
                            HabitRepeatType.WEEKLY_X -> {
                                val doneAll = habitWithStats.weeklyDoneThisWeek >= habit.weeklyTargetCount
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (doneAll) KairoSecondary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, if (doneAll) KairoSecondary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f))
                                ) {
                                    Text(
                                        text = "${habitWithStats.weeklyDoneThisWeek}/${habit.weeklyTargetCount} this wk",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (doneAll) KairoSecondary else Color(0xFFC8C4D9),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            HabitRepeatType.SPECIFIC_DAYS -> {
                                if (!habitWithStats.isTodayScheduled) {
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                    ) {
                                        Text(
                                            text = "Rest Day",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    // If Target Habit: Show Live Interactive Stepper
                    if (habit.type == HabitType.TARGET) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(
                                    1.dp,
                                    if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary.copy(alpha = 0.45f)
                                    else Color.White.copy(alpha = 0.12f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable(enabled = habitWithStats.todayValue > 0) { onDecrement() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = if (habitWithStats.todayValue > 0) Color.White else Color.Gray.copy(alpha = 0.5f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = "${habitWithStats.todayValue} / ${habit.targetValue} ${habit.targetUnit}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary else Color.White
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    val canIncrement = habitWithStats.todayValue < habit.targetValue
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (canIncrement) KairoPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                            .clickable(enabled = canIncrement) { onIncrement() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = if (canIncrement) KairoPrimary else Color.White.copy(alpha = 0.25f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Mini progress bar fill
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(habitWithStats.todayProgressFraction)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary else KairoPrimary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Divider line
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Swipable Compact Date Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    habitWithStats.days.forEach { dayInfo ->
                        DayCheckboxNode(
                            dayInfo = dayInfo,
                            isTargetType = habit.type == HabitType.TARGET,
                            targetValue = habit.targetValue,
                            onToggle = { onDayClick(dayInfo.date) }
                        )
                    }
                }
            }

            // RIGHT: 3 stacked indicator buttons (Streak / Done / Missed)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                // 1. Streak Button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = Color(0xFFFCD34D),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${habitWithStats.streak}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCD34D)
                        )
                    }
                }

                // 2. Completed / Done Button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KairoSecondary.copy(alpha = 0.16f))
                        .border(1.dp, KairoSecondary.copy(alpha = 0.40f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = KairoSecondary,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "${habitWithStats.doneCount}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = KairoSecondary
                        )
                    }
                }

                // 3. Not Completed / Missed Button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF43F5E).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Missed",
                            tint = Color(0xFFFDA4AF),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "${habitWithStats.missedCount}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDA4AF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCheckboxNode(
    dayInfo: HabitDayInfo,
    isTargetType: Boolean,
    targetValue: Int,
    onToggle: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pop_bounce",
        finishedListener = { isPressed = false }
    )

    val isFuture = dayInfo.date.isAfter(LocalDate.now())
    val isTargetDone = isTargetType && (dayInfo.value >= targetValue || dayInfo.state == HabitDayState.DONE)
    val isTargetPartial = isTargetType && (dayInfo.value in 1 until targetValue) && dayInfo.state != HabitDayState.MISSED

    Column(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Date Number and Day of Week
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${dayInfo.dayNumber}",
                fontSize = 10.sp,
                fontWeight = if (dayInfo.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = when {
                    dayInfo.isToday -> KairoPrimary
                    isFuture -> Color(0xFF64748B)
                    else -> Color(0xFF94A3B8)
                }
            )
            Spacer(modifier = Modifier.width(1.dp))
            Text(
                text = dayInfo.dayLabel,
                fontSize = 8.sp,
                fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    dayInfo.isToday -> KairoPrimary.copy(alpha = 0.85f)
                    isFuture -> Color(0xFF475569)
                    else -> Color(0xFF64748B)
                }
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Checkbox Box
        if (dayInfo.isRestDay) {
            // Rest Day indicator
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "—",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .scale(scaleAnim)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        when {
                            isTargetDone || dayInfo.state == HabitDayState.DONE -> {
                                Modifier
                                    .background(KairoSecondary.copy(alpha = 0.20f))
                                    .border(1.2.dp, KairoSecondary, RoundedCornerShape(6.dp))
                            }
                            isTargetPartial -> {
                                Modifier
                                    .background(KairoSecondary.copy(alpha = 0.10f))
                                    .border(1.dp, KairoSecondary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            }
                            dayInfo.state == HabitDayState.MISSED -> {
                                Modifier
                                    .background(Color(0xFFF43F5E).copy(alpha = 0.12f))
                                    .border(1.2.dp, Color(0xFFFB7185).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            }
                            isFuture -> {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.015f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                            }
                            else -> {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(
                                        if (dayInfo.isToday) 1.5.dp else 1.dp,
                                        if (dayInfo.isToday) KairoPrimary else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(6.dp)
                                    )
                            }
                        }
                    )
                    .clickable(enabled = !isFuture && !dayInfo.isRestDay) {
                        isPressed = true
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isTargetDone || dayInfo.state == HabitDayState.DONE -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = KairoSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    isTargetPartial -> {
                        Text(
                            text = "${dayInfo.value}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = KairoSecondary
                        )
                    }
                    dayInfo.state == HabitDayState.MISSED -> {
                        if (dayInfo.value > 0) {
                            Text(
                                text = "✕${dayInfo.value}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFB7185)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Missed",
                                tint = Color(0xFFFB7185),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
