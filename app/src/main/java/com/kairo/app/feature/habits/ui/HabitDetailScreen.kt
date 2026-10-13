package com.kairo.app.feature.habits.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayRecord
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerLow
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.feature.habits.model.HabitNoteHelper
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class DetailNoteDialogState(
    val habit: Habit,
    val date: LocalDate,
    val state: HabitDayState,
    val initialNote: String? = null,
    val isAppend: Boolean = false,
    val countIndex: Int? = null
)

@Composable
fun HabitDetailScreen(
    habitWithStats: HabitWithStats,
    onBack: () -> Unit,
    onCheckInToday: () -> Unit,
    onEditHabit: (Habit) -> Unit,
    onDeleteHabit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentHabit by remember(habitWithStats) { mutableStateOf(habitWithStats.habit) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var activeNoteDialog by remember { mutableStateOf<DetailNoteDialogState?>(null) }

    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    LaunchedEffect(Unit) {
        HabitRepository.autoMarkMissedPastDays()
    }

    val streakUnit = when (currentHabit.repeatType) {
        HabitRepeatType.WEEKLY_X -> "Weeks"
        HabitRepeatType.SPECIFIC_DAYS -> "Sessions"
        HabitRepeatType.EVERYDAY -> "Days"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
            .statusBarsPadding()
    ) {
        // App Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Habit Analytics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0xFFC8C4D9)
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.background(KairoSurfaceContainerHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("Share", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFFC8C4D9)) },
                        onClick = {
                            showOverflowMenu = false
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "I'm tracking \"${currentHabit.title}\" with Kairo! Current streak: ${habitWithStats.streak} $streakUnit.")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Habit")
                            context.startActivity(shareIntent)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFFC8C4D9)) },
                        onClick = {
                            showOverflowMenu = false
                            showEditDialog = true
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFFFB4AB)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFFB4AB)) },
                        onClick = {
                            showOverflowMenu = false
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Habit Overview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KairoSurfaceContainer)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with circular stroke
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val sweep = (habitWithStats.todayProgressFraction * 360f).coerceIn(0f, 360f)
                            Canvas(modifier = Modifier.size(60.dp)) {
                                drawCircle(
                                    color = Color(0xFF33343C),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                                if (sweep > 0f) {
                                    drawArc(
                                        color = KairoSecondary,
                                        startAngle = -90f,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (currentHabit.type == HabitType.TARGET) Icons.Default.Speed else Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary else KairoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            if (habitWithStats.todayProgressFraction >= 1f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(KairoSecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title & Dynamic Schedule Tags
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Schedule Badge
                                val scheduleText = when (currentHabit.repeatType) {
                                    HabitRepeatType.EVERYDAY -> "Everyday"
                                    HabitRepeatType.SPECIFIC_DAYS -> formatRepeatDaysShort(currentHabit.repeatDays)
                                    HabitRepeatType.WEEKLY_X -> "${currentHabit.weeklyTargetCount}x / wk"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(KairoPrimary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = scheduleText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KairoPrimary
                                    )
                                }

                                // Type Badge
                                val typeText = if (currentHabit.type == HabitType.TARGET) "Target" else "Check-in"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(KairoSurfaceContainerHigh)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = typeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFC8C4D9)
                                    )
                                }

                                if (currentHabit.repeatType == HabitRepeatType.WEEKLY_X) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(KairoSecondary.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${habitWithStats.weeklyDoneThisWeek}/${currentHabit.weeklyTargetCount} this wk",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KairoSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentHabit.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            if (currentHabit.type == HabitType.TARGET) {
                                Text(
                                    text = "Target: ${currentHabit.targetValue} ${currentHabit.targetUnit} daily",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // Streak Pills Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current Streak
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(KairoSurfaceContainerLow)
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "\uD83D\uDD25", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = "Current Streak", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "${habitWithStats.streak} $streakUnit",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Best Streak
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(KairoSurfaceContainerLow)
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⭐", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = "Best Streak", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "${habitWithStats.bestStreak} $streakUnit",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Today's Interactive Check-in & Stepper Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KairoSurfaceContainer)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Today's Action",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (!habitWithStats.isTodayScheduled) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Rest Day",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        if (habitWithStats.todayProgressFraction >= 1f) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(KairoSecondary.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Completed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KairoSecondary
                                )
                            }
                        }
                    }

                    if (currentHabit.type == HabitType.TARGET) {
                        // Interactive Target Stepper
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${habitWithStats.todayValue} / ${currentHabit.targetValue} ${currentHabit.targetUnit}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary else Color.White
                                )
                                Text(
                                    text = "${(habitWithStats.todayProgressFraction * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary else Color(0xFF94A3B8)
                                )
                            }

                            // Linear Progress Fill Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = habitWithStats.todayProgressFraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary else KairoPrimary)
                                )
                            }

                            // Stepper Controls
                            val canDecrementToday = habitWithStats.todayValue > 0
                            val canIncrementToday = habitWithStats.todayValue < currentHabit.targetValue

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Decrement -1
                                Surface(
                                    onClick = {
                                        HabitRepository.incrementHabitDay(currentHabit.id, today, -1)
                                    },
                                    enabled = canDecrementToday,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (canDecrementToday) KairoSurfaceContainerHigh else Color.White.copy(alpha = 0.04f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (canDecrementToday) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrement", tint = if (canDecrementToday) Color.White else Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("-1", color = if (canDecrementToday) Color.White else Color.Gray.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Increment +1
                                Surface(
                                    onClick = {
                                        val nextVal = habitWithStats.todayValue + 1
                                        HabitRepository.incrementHabitDay(currentHabit.id, today, 1)
                                        activeNoteDialog = DetailNoteDialogState(
                                            habit = currentHabit,
                                            date = today,
                                            state = HabitDayState.DONE,
                                            initialNote = null,
                                            countIndex = nextVal
                                        )
                                    },
                                    enabled = canIncrementToday,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (canIncrementToday) KairoPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (canIncrementToday) KairoPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increment", tint = if (canIncrementToday) KairoPrimary else Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+1", color = if (canIncrementToday) KairoPrimary else Color.Gray.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Complete Goal Button
                                Surface(
                                    onClick = {
                                        val isAlreadyDone = habitWithStats.todayProgressFraction >= 1f
                                        if (!isAlreadyDone) {
                                            HabitRepository.setHabitDayValue(currentHabit.id, today, currentHabit.targetValue)
                                        }
                                        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        val existingNote = HabitRepository.getHabitLogs(currentHabit.id)[todayStr]?.note
                                        activeNoteDialog = DetailNoteDialogState(
                                            habit = currentHabit,
                                            date = today,
                                            state = HabitDayState.DONE,
                                            initialNote = existingNote
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (habitWithStats.todayProgressFraction >= 1f) KairoSecondary else KairoSecondary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, KairoSecondary.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (habitWithStats.todayProgressFraction >= 1f) Color(0xFF003822) else KairoSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (habitWithStats.todayProgressFraction >= 1f) "Completed" else "Done (${currentHabit.targetValue})",
                                            color = if (habitWithStats.todayProgressFraction >= 1f) Color(0xFF003822) else KairoSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Show Today's Note if recorded
                            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val todayRecord = HabitRepository.getHabitLogs(currentHabit.id)[todayStr]
                            if (!todayRecord?.note.isNullOrBlank()) {
                                Surface(
                                    onClick = {
                                        activeNoteDialog = DetailNoteDialogState(
                                            habit = currentHabit,
                                            date = today,
                                            state = todayRecord!!.state,
                                            initialNote = todayRecord.note
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.04f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EditNote,
                                                contentDescription = null,
                                                tint = KairoSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Today's Note: ${HabitNoteHelper.formatDisplayNote(todayRecord.note)}",
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Note",
                                            tint = Color(0xFFC8C4D9),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Yes/No Quick Check-in
                        val isDone = habitWithStats.todayProgressFraction >= 1f
                        Surface(
                            onClick = onCheckInToday,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDone) KairoSecondary else KairoPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDone) KairoSecondary else KairoPrimary.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Default.Check else Icons.Default.TaskAlt,
                                    contentDescription = null,
                                    tint = if (isDone) Color(0xFF003822) else KairoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isDone) "Completed Today (Tap to unmark)" else "Mark Complete for Today",
                                    color = if (isDone) Color(0xFF003822) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 3. 4-Stat Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Completions",
                    value = "${habitWithStats.doneCount} times",
                    subtext = "Total logged",
                    icon = Icons.Default.TaskAlt,
                    accentColor = KairoSecondary,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    label = "Adherence",
                    value = "${String.format(Locale.US, "%.1f", habitWithStats.adherenceRate)}%",
                    subtext = when (currentHabit.repeatType) {
                        HabitRepeatType.SPECIFIC_DAYS -> "Scheduled days"
                        HabitRepeatType.WEEKLY_X -> "Weekly quota"
                        HabitRepeatType.EVERYDAY -> "Consistency"
                    },
                    icon = Icons.Default.Verified,
                    accentColor = KairoPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Missed Days",
                    value = "${habitWithStats.missedCount} days",
                    subtext = "Past scheduled",
                    icon = Icons.Default.EventBusy,
                    accentColor = Color(0xFFFFB77D),
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    label = "Cadence",
                    value = when (currentHabit.repeatType) {
                        HabitRepeatType.WEEKLY_X -> "${currentHabit.weeklyTargetCount}x / wk"
                        HabitRepeatType.SPECIFIC_DAYS -> "${currentHabit.repeatDays.size}d / wk"
                        HabitRepeatType.EVERYDAY -> if (currentHabit.type == HabitType.TARGET) "${currentHabit.targetValue} ${currentHabit.targetUnit}/d" else "Daily"
                    },
                    subtext = "Scheduled rate",
                    icon = Icons.Default.Speed,
                    accentColor = KairoSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. Monthly Heatmap Card
            MonthlyHeatmapSection(
                habit = currentHabit,
                onPromptNote = { date, state, note, isAppend, countIndex ->
                    activeNoteDialog = DetailNoteDialogState(
                        habit = currentHabit,
                        date = date,
                        state = state,
                        initialNote = note,
                        isAppend = isAppend,
                        countIndex = countIndex
                    )
                }
            )

            // 5. Notes & Reasons Analytics (Donut)
            HabitNotesDonutAnalytics(
                habit = currentHabit
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Edit Habit Dialog
    if (showEditDialog) {
        CreateHabitDialog(
            initialHabit = currentHabit,
            onDismiss = { showEditDialog = false },
            onConfirm = { title, type, targetValue, targetUnit, repeatType, repeatDays, weeklyTargetCount, reminderEnabled, reminderTime, reminderToneUri, reminderToneTitle ->
                val updated = currentHabit.copy(
                    title = title,
                    type = type,
                    targetValue = targetValue,
                    targetUnit = targetUnit,
                    repeatType = repeatType,
                    repeatDays = repeatDays,
                    weeklyTargetCount = weeklyTargetCount,
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    reminderToneUri = reminderToneUri,
                    reminderToneTitle = reminderToneTitle,
                    updatedAt = System.currentTimeMillis()
                )
                currentHabit = updated
                HabitRepository.updateHabit(updated)
                onEditHabit(updated)
                showEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete Habit?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${currentHabit.title}\"? All habit history and streak data will be permanently deleted from Supabase.",
                    color = Color(0xFFC8C4D9),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteHabit(currentHabit.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB4AB))
                ) {
                    Text("Delete", color = Color(0xFF690005), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Habit Note BottomSheet (Prompt for Increment Note, Done note or Missed reason)
    activeNoteDialog?.let { dialogState ->
        HabitIncrementNoteBottomSheet(
            habitTitle = dialogState.habit.title,
            habitId = dialogState.habit.id,
            countIndex = dialogState.countIndex,
            targetValue = dialogState.habit.targetValue,
            targetUnit = dialogState.habit.targetUnit,
            isMissed = dialogState.state == HabitDayState.MISSED,
            initialNote = dialogState.initialNote,
            onDismiss = { activeNoteDialog = null },
            onSaveNote = { noteText ->
                if (dialogState.countIndex != null) {
                    HabitRepository.setIncrementNote(dialogState.habit.id, dialogState.date, dialogState.countIndex, noteText)
                } else {
                    HabitRepository.setHabitDayNote(dialogState.habit.id, dialogState.date, noteText)
                }
                activeNoteDialog = null
            }
        )
    }
}

@Composable
fun MonthlyHeatmapSection(
    habit: Habit,
    onPromptNote: (LocalDate, HabitDayState, String?, Boolean, Int?) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val allLogsMap: Map<String, Map<String, HabitDayRecord>> by HabitRepository.logsMap.collectAsState()
    val habitLogs: Map<String, HabitDayRecord> = allLogsMap[habit.id] ?: emptyMap()
    val today = remember { LocalDate.now() }

    var selectedYear by remember { mutableIntStateOf(today.year) }
    var selectedMonth by remember { mutableIntStateOf(today.monthValue) } // 1..12
    var selectedDayDate by remember { mutableStateOf<LocalDate?>(today) }
    var showPickerMenu by remember { mutableStateOf(false) }

    val yearMonth = remember(selectedYear, selectedMonth) { YearMonth.of(selectedYear, selectedMonth) }
    val daysInMonth = yearMonth.lengthOfMonth()
    val startDayOfWeek = yearMonth.atDay(1).dayOfWeek.value // 1 (Mon) .. 7 (Sun)
    val startOffset = startDayOfWeek - 1 // 0..6
    val totalCells = startOffset + daysInMonth
    val numRows = (totalCells + 6) / 7

    val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    // Month stats
    val (monthDoneCount, monthMissedCount) = remember(habitLogs, selectedYear, selectedMonth) {
        var done = 0
        var missed = 0
        for (day in 1..daysInMonth) {
            val cellDate = LocalDate.of(selectedYear, selectedMonth, day)
            val dStr = cellDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val rec = habitLogs[dStr]
            val isRest = (habit.repeatType == HabitRepeatType.SPECIFIC_DAYS && !habit.repeatDays.contains(cellDate.dayOfWeek.value))
            val isDone = rec?.state == HabitDayState.DONE || (habit.type == HabitType.TARGET && (rec?.value ?: 0) >= habit.targetValue && habit.targetValue > 0)
            if (isDone) {
                done++
            } else if (!isRest && (rec?.state == HabitDayState.MISSED || cellDate.isBefore(today))) {
                missed++
            }
        }
        done to missed
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KairoSurfaceContainer)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header with navigation & picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = KairoSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Monthly Heatmap",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Month / Year Stepper & Picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (selectedMonth == 1) {
                                selectedMonth = 12
                                selectedYear -= 1
                            } else {
                                selectedMonth -= 1
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Month",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(KairoSurfaceContainerHigh)
                                .clickable { showPickerMenu = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$monthName $selectedYear",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select month/year",
                                tint = Color(0xFFC8C4D9),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showPickerMenu,
                            onDismissRequest = { showPickerMenu = false },
                            modifier = Modifier.background(KairoSurfaceContainerHigh)
                        ) {
                            // Year stepper
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedYear -= 1 },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Text("$selectedYear", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                IconButton(
                                    onClick = { selectedYear += 1 },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                            // 12 Months
                            val monthNames = (1..12).map { m ->
                                java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            }
                            monthNames.chunked(3).forEachIndexed { rowIndex, rowMonths ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowMonths.forEachIndexed { colIndex, mName ->
                                        val mVal = rowIndex * 3 + colIndex + 1
                                        val isSelected = mVal == selectedMonth
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) KairoPrimaryContainer else Color.Transparent)
                                                .clickable {
                                                    selectedMonth = mVal
                                                    showPickerMenu = false
                                                }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = mName,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFFC8C4D9)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (selectedMonth == 12) {
                                selectedMonth = 1
                                selectedYear += 1
                            } else {
                                selectedMonth += 1
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Month",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Summary text for selected month
            Text(
                text = "$monthDoneCount days completed • $monthMissedCount days missed in $monthName $selectedYear",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )

            // Day of week column headers: Mon Tue Wed Thu Fri Sat Sun
            val weekDayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weekDayHeaders.forEach { h ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = h,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Monthly Heatmap Calendar Grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until numRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            if (cellIndex < startOffset || cellIndex >= totalCells) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                )
                            } else {
                                val dayNum = cellIndex - startOffset + 1
                                val cellDate = LocalDate.of(selectedYear, selectedMonth, dayNum)
                                val cellDateStr = cellDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val record = habitLogs[cellDateStr]
                                val state = record?.state ?: HabitDayState.UNMARKED
                                val recordVal = record?.value ?: 0
                                val target = habit.targetValue.coerceAtLeast(1)

                                val isDone = state == HabitDayState.DONE || (habit.type == HabitType.TARGET && recordVal >= target)
                                val isPast = cellDate.isBefore(today)
                                val isRestDay = (habit.repeatType == HabitRepeatType.SPECIFIC_DAYS && !habit.repeatDays.contains(cellDate.dayOfWeek.value) && !isDone)
                                val isMissed = state == HabitDayState.MISSED || (isPast && !isDone && !isRestDay)
                                val isPartial = habit.type == HabitType.TARGET && recordVal in 1 until target && !isMissed
                                val isSelected = selectedDayDate == cellDate
                                val isToday = cellDate == today

                                val bgColor = when {
                                    isDone -> KairoSecondary
                                    isMissed -> Color(0xFFFFB77D)
                                    isPartial -> KairoSecondary.copy(alpha = 0.35f)
                                    isRestDay -> Color.White.copy(alpha = 0.03f)
                                    else -> KairoSurfaceContainerHighest
                                }
                                val textColor = when {
                                    isDone -> Color(0xFF003822)
                                    isMissed -> Color(0xFF4E2600)
                                    isPartial -> Color.White
                                    isRestDay -> Color(0xFF64748B)
                                    else -> Color(0xFFCBD5E1)
                                }

                                val borderModifier = if (isSelected) {
                                    Modifier.border(2.dp, KairoPrimary, RoundedCornerShape(8.dp))
                                } else if (isToday) {
                                    Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                } else if (isPartial) {
                                    Modifier.border(1.dp, KairoSecondary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                } else {
                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bgColor)
                                        .then(borderModifier)
                                        .clickable {
                                            selectedDayDate = cellDate
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = textColor
                                        )
                                        if (isRestDay) {
                                            Text(
                                                text = "—",
                                                fontSize = 9.sp,
                                                color = Color(0xFF64748B),
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 9.sp
                                            )
                                        } else if (isDone) {
                                            // completed day
                                        } else if (isMissed) {
                                            Text(
                                                text = if (habit.type == HabitType.TARGET) "${recordVal}✕" else "✕",
                                                fontSize = 9.sp,
                                                color = Color(0xFF4E2600),
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 9.sp
                                            )
                                        } else if (isPartial) {
                                            Text(
                                                text = "$recordVal",
                                                fontSize = 9.sp,
                                                color = KairoSecondary,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Day Status & Direct Edit Panel
            if (selectedDayDate != null) {
                val sDate = selectedDayDate!!
                val sDateStr = sDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val dayRecord = habitLogs[sDateStr] ?: HabitDayRecord()
                val dayState = dayRecord.state
                val dayValue = dayRecord.value
                val target = habit.targetValue.coerceAtLeast(1)

                val isPastSelected = sDate.isBefore(today)
                val isDateDone = dayState == HabitDayState.DONE || (habit.type == HabitType.TARGET && dayValue >= target)
                val isDateRest = (habit.repeatType == HabitRepeatType.SPECIFIC_DAYS && !habit.repeatDays.contains(sDate.dayOfWeek.value))
                val isSelectedMissed = dayState == HabitDayState.MISSED || (isPastSelected && !isDateDone && !isDateRest)
                val isDatePartial = habit.type == HabitType.TARGET && dayValue in 1 until target && !isSelectedMissed
                val formattedSelectedDate = sDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KairoSurfaceContainerHigh)
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formattedSelectedDate,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (sDate == today) "Today" else if (sDate.isAfter(today)) "Upcoming day" else "Past day",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            // Current state badge
                            val badgeBg = when {
                                isDateDone -> KairoSecondary.copy(alpha = 0.20f)
                                isSelectedMissed -> Color(0xFFFFB77D).copy(alpha = 0.20f)
                                isDatePartial -> KairoSecondary.copy(alpha = 0.15f)
                                isDateRest -> Color.White.copy(alpha = 0.08f)
                                else -> KairoSurfaceContainerHighest
                            }
                            val badgeText = when {
                                isDateDone -> "Completed"
                                isSelectedMissed -> if (habit.type == HabitType.TARGET) "Missed (${dayValue}✕/$target ${habit.targetUnit})" else "Missed"
                                isDatePartial -> "$dayValue / $target ${habit.targetUnit}"
                                isDateRest -> "Rest Day"
                                else -> "Unmarked"
                            }
                            val badgeTextColor = when {
                                isDateDone -> KairoSecondary
                                isSelectedMissed -> Color(0xFFFFB77D)
                                isDatePartial -> KairoSecondary
                                isDateRest -> Color(0xFF94A3B8)
                                else -> Color(0xFFC8C4D9)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeTextColor
                                )
                            }
                        }

                        // Target stepper vs Yes/No buttons
                        if (sDate.isAfter(today)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.04f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Future date — habit actions are locked until this day arrives.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        } else if (habit.type == HabitType.TARGET) {
                            Text(
                                text = "Adjust count for $formattedSelectedDate (syncs to Supabase):",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )

                            val canDecrement = dayValue > 0
                            val canIncrement = dayValue < target

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // -1
                                Surface(
                                    onClick = {
                                        HabitRepository.incrementHabitDay(habit.id, sDate, -1)
                                    },
                                    enabled = canDecrement,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (canDecrement) KairoSurfaceContainerHighest else Color.White.copy(alpha = 0.04f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (canDecrement) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "-1", tint = if (canDecrement) Color.White else Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("-1", color = if (canDecrement) Color.White else Color.Gray.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // +1
                                Surface(
                                    onClick = {
                                        val nextVal = dayValue + 1
                                        HabitRepository.incrementHabitDay(habit.id, sDate, 1)
                                        onPromptNote(sDate, HabitDayState.DONE, null, false, nextVal)
                                    },
                                    enabled = canIncrement,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (canIncrement) KairoPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (canIncrement) KairoPrimary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.04f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+1", tint = if (canIncrement) KairoPrimary else Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("+1", color = if (canIncrement) KairoPrimary else Color.Gray.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Target Full Value
                                Surface(
                                    onClick = {
                                        HabitRepository.setHabitDayValue(habit.id, sDate, target)
                                        onPromptNote(sDate, HabitDayState.DONE, dayRecord.note, false, null)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDateDone) KairoSecondary else KairoSecondary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, KairoSecondary.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (isDateDone) Color(0xFF003822) else KairoSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Done ($target)",
                                            color = if (isDateDone) Color(0xFF003822) else KairoSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Missed Button for Target habit
                                val isTargetMissed = dayState == HabitDayState.MISSED
                                Surface(
                                    onClick = {
                                        HabitRepository.setHabitDayState(habit.id, sDate, HabitDayState.MISSED)
                                        onPromptNote(sDate, HabitDayState.MISSED, dayRecord.note, false, null)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isTargetMissed) Color(0xFFFFB77D) else KairoSurfaceContainerHighest,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isTargetMissed) Color(0xFFFFB77D) else Color.White.copy(alpha = 0.08f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (isTargetMissed) Color(0xFF4E2600) else Color(0xFFCBD5E1),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Missed",
                                            color = if (isTargetMissed) Color(0xFF4E2600) else Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Clear (Only for today — past days only have Done / Missed)
                                if (!isPastSelected) {
                                    Surface(
                                        onClick = {
                                            HabitRepository.setHabitDayState(habit.id, sDate, HabitDayState.UNMARKED, clearNote = true)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = KairoSurfaceContainerHighest,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Clear", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Yes/No Status action chips
                            Text(
                                text = "Set Status (syncs to Supabase):",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Completed Button (Instant, no note dialog for Yes/No)
                                val isDone = dayState == HabitDayState.DONE
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDone) KairoSecondary else KairoSurfaceContainerHighest)
                                        .border(
                                            1.dp,
                                            if (isDone) KairoSecondary else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            HabitRepository.setHabitDayState(habit.id, sDate, HabitDayState.DONE)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (isDone) Color(0xFF003822) else Color(0xFFCBD5E1),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Done",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDone) Color(0xFF003822) else Color(0xFFCBD5E1)
                                        )
                                    }
                                }

                                // Missed Button (Prompts for Missed reason)
                                val isMissed = dayState == HabitDayState.MISSED
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isMissed) Color(0xFFFFB77D) else KairoSurfaceContainerHighest)
                                        .border(
                                            1.dp,
                                            if (isMissed) Color(0xFFFFB77D) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            HabitRepository.setHabitDayState(habit.id, sDate, HabitDayState.MISSED)
                                            onPromptNote(sDate, HabitDayState.MISSED, dayRecord.note, false, null)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (isMissed) Color(0xFF4E2600) else Color(0xFFCBD5E1),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Missed",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMissed) Color(0xFF4E2600) else Color(0xFFCBD5E1)
                                        )
                                    }
                                }

                                // Clear / Reset Button (Only for today — past days only have Done / Missed)
                                if (!isPastSelected) {
                                    val isUnmarked = dayState == HabitDayState.UNMARKED
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isUnmarked) Color.White.copy(alpha = 0.12f) else KairoSurfaceContainerHighest)
                                            .border(
                                                1.dp,
                                                if (isUnmarked) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                HabitRepository.setHabitDayState(habit.id, sDate, HabitDayState.UNMARKED, clearNote = true)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                tint = Color(0xFFCBD5E1),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Clear",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Day Note Display / Add Note chip (only for past and present days)
                        if (!sDate.isAfter(today)) {
                            if (!dayRecord.note.isNullOrBlank()) {
                                Surface(
                                    onClick = {
                                        onPromptNote(sDate, if (isDateDone) HabitDayState.DONE else HabitDayState.MISSED, dayRecord.note, false, null)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.04f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EditNote,
                                                contentDescription = null,
                                                tint = if (isDateDone) KairoSecondary else Color(0xFFFFB77D),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Note: ${HabitNoteHelper.formatDisplayNote(dayRecord.note)}",
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Note",
                                            tint = Color(0xFFC8C4D9),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else if ((isDateDone && habit.type == HabitType.TARGET) || dayState == HabitDayState.MISSED) {
                                Surface(
                                    onClick = {
                                        onPromptNote(sDate, if (isDateDone) HabitDayState.DONE else HabitDayState.MISSED, null, false, null)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.03f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = null,
                                            tint = if (isDateDone) KairoSecondary else Color(0xFFFFB77D),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (isDateDone) "+ Add completion note" else "+ Add missed reason",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDateDone) KairoSecondary else Color(0xFFFFB77D)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Legend", fontSize = 11.sp, color = Color.Gray)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(KairoSecondary))
                        Text("Done", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                    if (habit.type == HabitType.TARGET) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(KairoSecondary.copy(alpha = 0.35f)))
                            Text("Partial", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFFB77D)))
                        Text("Missed", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                    if (habit.repeatType == HabitRepeatType.SPECIFIC_DAYS) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.08f)))
                            Text("Rest", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KairoSurfaceContainer)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, fontSize = 12.sp, color = Color(0xFF94A3B8))
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = accentColor, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = subtext, fontSize = 11.sp, color = accentColor)
            }
        }
    }
}

private fun formatRepeatDaysShort(days: List<Int>): String {
    val dayMap = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    return days.sorted().mapNotNull { dayMap[it] }.joinToString(", ")
}
