package com.kairo.app.feature.habits.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLow

@Composable
fun HabitDetailScreen(
    habitWithStats: HabitWithStats,
    onBack: () -> Unit,
    onCheckInToday: () -> Unit,
    onEditHabit: (Habit) -> Unit,
    onDeleteHabit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedTooltip by remember { mutableStateOf("Oct 24: Completed 100% Target") }
    var selectedPeriod by remember { mutableStateOf("2026") }

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
                        onClick = { showOverflowMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFFC8C4D9)) },
                        onClick = {
                            showOverflowMenu = false
                            onEditHabit(habit)
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFFFB4AB)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFFB4AB)) },
                        onClick = {
                            showOverflowMenu = false
                            onDeleteHabit(habit.id)
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
                                text = habit.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            if (habit.targetValue > 1) {
                                Text(
                                    text = "Target: ${habit.targetValue} ${habit.targetUnit} daily",
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
                                Text(text = "🔥", fontSize = 18.sp)
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
                                Text(text = "🏆", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = "All-Time Best", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Text(text = "${habitWithStats.bestStreak} Days", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    // Check-in CTA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onCheckInToday,
                            colors = ButtonDefaults.buttonColors(containerColor = KairoSecondary),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Check In Today", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(KairoSurfaceContainerHigh)
                                .clickable { onEditHabit(habit) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(20.dp))
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
                    value = "${String.format(java.util.Locale.US, "%.1f", habitWithStats.adherenceRate)}%",
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
                    value = if (habit.targetValue > 1) "${habit.targetValue} ${habit.targetUnit}/d" else "1.0 /d",
                    subtext = "Steady pace",
                    icon = Icons.Default.Speed,
                    accentColor = KairoSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Consistency Heatmap Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KairoSurfaceContainer)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Title and Period Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarViewWeek, null, tint = KairoSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Consistency Heatmap", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(KairoSurfaceContainerHigh)
                                .padding(2.dp)
                        ) {
                            listOf("2026", "6M", "90D").forEach { p ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(if (selectedPeriod == p) KairoPrimaryContainer else Color.Transparent)
                                        .clickable { selectedPeriod = p }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = p,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedPeriod == p) Color.White else Color(0xFFC8C4D9)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "${habitWithStats.doneCount * 3 + 12} active days in 2026 • Longest streak: ${habitWithStats.bestStreak} days",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    // Tooltip banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(KairoSurfaceContainerHighest)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(KairoSecondary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = selectedTooltip, fontSize = 12.sp, color = Color.White)
                            }
                            Text(text = "100% Target", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KairoSecondary)
                        }
                    }

                    // Heatmap Grid: 7 rows (Mon-Sun) x 18 cols
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Day labels
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.height(100.dp).padding(end = 4.dp)
                        ) {
                            Text("M", fontSize = 10.sp, color = Color.Gray)
                            Text("W", fontSize = 10.sp, color = Color.Gray)
                            Text("F", fontSize = 10.sp, color = Color.Gray)
                        }

                        // Grid Columns (weeks)
                        for (w in 0 until 18) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (d in 0 until 7) {
                                    val cellLevel = (w * 7 + d) % 5
                                    val cellColor = when (cellLevel) {
                                        0 -> KairoSurfaceContainerHighest
                                        1 -> KairoSecondary.copy(alpha = 0.30f)
                                        2 -> KairoSecondary.copy(alpha = 0.55f)
                                        3 -> KairoSecondary.copy(alpha = 0.80f)
                                        else -> KairoSecondary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cellColor)
                                            .clickable {
                                                selectedTooltip = "Week ${w + 1}, Day ${d + 1}: Done"
                                            }
                                    )
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
                        Text("Consistency", fontSize = 11.sp, color = Color.Gray)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Less", fontSize = 10.sp, color = Color.Gray)
                            Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(KairoSurfaceContainerHighest))
                            Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(KairoSecondary.copy(alpha = 0.3f)))
                            Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(KairoSecondary.copy(alpha = 0.6f)))
                            Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(KairoSecondary))
                            Text("More", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // 4. Completion Distribution Histogram (Time of Day)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(KairoSurfaceContainer)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = KairoSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Completion Distribution", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(KairoPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Time of Day", fontSize = 11.sp, color = KairoPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        text = "Hourly frequency breakdown of habit completions",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    // Bar Chart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KairoSurfaceContainerLow)
                            .padding(12.dp)
                    ) {
                        val hours = listOf(
                            "6a" to 12,
                            "8a" to 26,
                            "10a" to 34, // Peak
                            "12p" to 18,
                            "2p" to 22,
                            "5p" to 14,
                            "8p" to 8,
                            "10p" to 4
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            hours.forEach { (hr, pct) ->
                                val isPeak = hr == "10a"
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "$pct%",
                                        fontSize = 10.sp,
                                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isPeak) KairoSecondary else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height((pct * 2.5f).dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(
                                                if (isPeak) KairoSecondary
                                                else if (hr == "2p") KairoPrimaryContainer
                                                else KairoSecondary.copy(alpha = 0.45f)
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = hr,
                                        fontSize = 10.sp,
                                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isPeak) KairoSecondary else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    // Peak Flow Callout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(KairoSurfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Insights, null, tint = KairoSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Peak: 8:00 – 10:30 AM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "72% before 12 PM",
                                fontSize = 11.sp,
                                color = Color(0xFFC8C4D9)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
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
