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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
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
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    val scrollState = rememberScrollState()

    // Automatically scroll to today (the rightmost day) on first layout
    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KairoSurfaceContainer)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Drag Indicator Handle
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = Color(0xFF64748B), // slate-500
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp, end = 6.dp)
            )

            // Content column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Title and Badges row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Habit Title (clickable to open detail analytics)
                    Text(
                        text = habit.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clickable { onTitleClick() }
                    )

                    // Badges stack (Streak, Done, Missed)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Streak badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.25f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFFCD34D),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${habitWithStats.streak}d",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFCD34D)
                            )
                        }

                        // Done badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(KairoSecondary.copy(alpha = 0.15f))
                                .border(1.dp, KairoSecondary.copy(alpha = 0.30f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = KairoSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${habitWithStats.doneCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KairoSecondary
                            )
                        }

                        // Missed badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color(0xFFF43F5E).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.25f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color(0xFFFDA4AF),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${habitWithStats.missedCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFDA4AF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date Scroll Track
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    habitWithStats.days.forEach { dayInfo ->
                        DayToggleNode(
                            dayInfo = dayInfo,
                            onToggle = { onDayClick(dayInfo.date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayToggleNode(
    dayInfo: HabitDayInfo,
    onToggle: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pop_bounce",
        finishedListener = { isPressed = false }
    )

    Column(
        modifier = Modifier
            .then(
                if (dayInfo.isToday) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairoPrimary.copy(alpha = 0.10f))
                        .border(1.dp, KairoPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Date & Day header label
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${dayInfo.dayNumber}",
                fontSize = 11.sp,
                fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (dayInfo.isToday) KairoPrimary else Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = dayInfo.dayLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = if (dayInfo.isToday) KairoPrimary.copy(alpha = 0.8f) else Color(0xFF64748B)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Button
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(scaleAnim)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    when (dayInfo.state) {
                        HabitDayState.DONE -> {
                            Modifier
                                .background(KairoSecondary.copy(alpha = 0.18f))
                                .border(1.dp, KairoSecondary.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                        }
                        HabitDayState.MISSED -> {
                            Modifier
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        }
                        HabitDayState.UNMARKED -> {
                            Modifier
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(
                                    1.dp,
                                    if (dayInfo.isToday) KairoPrimary.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(8.dp)
                                )
                        }
                    }
                )
                .clickable {
                    isPressed = true
                    onToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            when (dayInfo.state) {
                HabitDayState.DONE -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = KairoSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                HabitDayState.MISSED -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Missed",
                        tint = Color(0xFFFB7185), // rose-400
                        modifier = Modifier.size(15.dp)
                    )
                }
                HabitDayState.UNMARKED -> {
                    // Empty square
                }
            }
        }
    }
}
