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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

    val context = LocalContext.current

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
                                putExtra(Intent.EXTRA_TEXT, "I'm tracking \"${currentHabit.title}\" with Kairo! Current streak: ${habitWithStats.streak} days.")
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
                            Canvas(modifier = Modifier.size(60.dp)) {
                                drawCircle(
                                    color = Color(0xFF33343C),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                                drawArc(
                                    color = KairoSecondary,
                                    startAngle = -90f,
                                    sweepAngle = 310f,
                                    useCenter = false,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = KairoSecondary,
                                modifier = Modifier.size(30.dp)
                            )
                            // Small check pill
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

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title & Tags
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(KairoPrimary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Discipline",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KairoPrimary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(KairoSurfaceContainerHigh)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Daily",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFC8C4D9)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentHabit.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            if (currentHabit.targetValue > 1) {
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
                                    Text(text = "${habitWithStats.streak} Days", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                                    Text(text = "${habitWithStats.bestStreak} Days", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 2. 4-Stat Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Completions",
                    value = "${habitWithStats.doneCount} times",
                    subtext = "+12% vs last mo",
                    icon = Icons.Default.TaskAlt,
                    accentColor = KairoSecondary,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    label = "Adherence",
                    value = "${String.format(Locale.US, "%.1f", habitWithStats.adherenceRate)}%",
                    subtext = "Top consistency",
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
                    subtext = "In 2026 total",
                    icon = Icons.Default.EventBusy,
                    accentColor = Color(0xFFFFB77D),
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    label = "Velocity",
                    value = if (currentHabit.targetValue > 1) "${currentHabit.targetValue} ${currentHabit.targetUnit}/d" else "1.0 /d",
                    subtext = "Steady pace",
                    icon = Icons.Default.Speed,
                    accentColor = KairoSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Monthly Heatmap Card
            MonthlyHeatmapSection(
                habitId = currentHabit.id
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
}

@Composable
fun MonthlyHeatmapSection(
    habitId: String,
    modifier: Modifier = Modifier
) {
    val allLogsMap by HabitRepository.logsMap.collectAsState()
    val habitLogs = allLogsMap[habitId] ?: emptyMap()
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
            val dStr = LocalDate.of(selectedYear, selectedMonth, day).format(DateTimeFormatter.ISO_LOCAL_DATE)
            when (habitLogs[dStr]) {
                HabitDayState.DONE -> done++
                HabitDayState.MISSED -> missed++
                else -> {}
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
                                val state = habitLogs[cellDateStr] ?: HabitDayState.UNMARKED
                                val isSelected = selectedDayDate == cellDate
                                val isToday = cellDate == today

                                val bgColor = when (state) {
                                    HabitDayState.DONE -> KairoSecondary
                                    HabitDayState.MISSED -> Color(0xFFFFB77D)
                                    HabitDayState.UNMARKED -> KairoSurfaceContainerHighest
                                }
                                val textColor = when (state) {
                                    HabitDayState.DONE -> Color(0xFF003822)
                                    HabitDayState.MISSED -> Color(0xFF4E2600)
                                    HabitDayState.UNMARKED -> Color(0xFFCBD5E1)
                                }

                                val borderModifier = if (isSelected) {
                                    Modifier.border(2.dp, KairoPrimary, RoundedCornerShape(8.dp))
                                } else if (isToday) {
                                    Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
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
                val dayState = habitLogs[sDateStr] ?: HabitDayState.UNMARKED
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
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(
                                        when (dayState) {
                                            HabitDayState.DONE -> KairoSecondary.copy(alpha = 0.20f)
                                            HabitDayState.MISSED -> Color(0xFFFFB77D).copy(alpha = 0.20f)
                                            HabitDayState.UNMARKED -> KairoSurfaceContainerHighest
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when (dayState) {
                                        HabitDayState.DONE -> "Completed"
                                        HabitDayState.MISSED -> "Missed"
                                        HabitDayState.UNMARKED -> "Unmarked"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (dayState) {
                                        HabitDayState.DONE -> KairoSecondary
                                        HabitDayState.MISSED -> Color(0xFFFFB77D)
                                        HabitDayState.UNMARKED -> Color(0xFFC8C4D9)
                                    }
                                )
                            }
                        }

                        // Status toggle action chips
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
                            // Completed Button
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
                                        HabitRepository.setHabitDayState(habitId, sDate, HabitDayState.DONE)
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

                            // Missed Button
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
                                        HabitRepository.setHabitDayState(habitId, sDate, HabitDayState.MISSED)
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

                            // Clear / Reset Button
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
                                        HabitRepository.setHabitDayState(habitId, sDate, HabitDayState.UNMARKED)
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
                        Text("Completed", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFFB77D)))
                        Text("Missed", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(KairoSurfaceContainerHighest))
                        Text("Unmarked", fontSize = 10.sp, color = Color(0xFF94A3B8))
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
