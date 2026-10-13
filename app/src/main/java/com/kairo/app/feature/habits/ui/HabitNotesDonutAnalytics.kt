package com.kairo.app.feature.habits.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitNoteHelper
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.sqrt

enum class HabitNoteTab {
    DONE,
    MISSED
}

private val DONE_COLORS = listOf(
    Color(0xFF34D399), // Emerald
    Color(0xFF38BDF8), // Sky Blue
    Color(0xFFA78BFA), // Violet
    Color(0xFFFBBF24), // Amber
    Color(0xFFF472B6), // Pink
    Color(0xFF2DD4BF), // Teal
    Color(0xFF818CF8), // Indigo
    Color(0xFFFB923C)  // Orange
)

private val MISSED_COLORS = listOf(
    Color(0xFFFFB77D), // Soft Peach
    Color(0xFFF87171), // Red
    Color(0xFFFB923C), // Amber Orange
    Color(0xFFF43F5E), // Rose
    Color(0xFFA78BFA), // Purple
    Color(0xFFE879F9), // Fuchsia
    Color(0xFF94A3B8)  // Slate
)

@Composable
fun HabitNotesDonutAnalytics(
    habit: Habit,
    modifier: Modifier = Modifier
) {
    val allLogsMap by HabitRepository.logsMap.collectAsState()
    val habitLogs = allLogsMap[habit.id] ?: emptyMap()

    // Tab selector: DONE vs MISSED
    var selectedTab by remember {
        mutableStateOf(if (habit.type == HabitType.TARGET) HabitNoteTab.DONE else HabitNoteTab.MISSED)
    }
    var selectedNote by remember { mutableStateOf<String?>(null) }

    // Filter logs for this habit by selected tab
    val targetState = if (selectedTab == HabitNoteTab.DONE) HabitDayState.DONE else HabitDayState.MISSED
    val relevantLogs = remember(habitLogs, selectedTab, habit.type) {
        habitLogs.filter { (_, rec) ->
            if (selectedTab == HabitNoteTab.DONE) {
                (rec.state == HabitDayState.DONE || rec.value > 0) && !rec.note.isNullOrBlank()
            } else {
                rec.state == HabitDayState.MISSED && !rec.note.isNullOrBlank()
            }
        }
    }

    // Group by trimmed note string and count occurrences (splitting multi-item notes)
    val noteCounts = remember(relevantLogs) {
        relevantLogs.values
            .flatMap { rec ->
                HabitNoteHelper.extractAllNotes(rec.note)
            }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    val totalNotesCount = remember(noteCounts) { noteCounts.sumOf { it.second } }

    // Palette
    val colorPalette = if (selectedTab == HabitNoteTab.DONE) DONE_COLORS else MISSED_COLORS

    // Reset or keep valid selectedNote
    LaunchedEffect(noteCounts) {
        if (selectedNote != null && noteCounts.none { it.first == selectedNote }) {
            selectedNote = noteCounts.firstOrNull()?.first
        } else if (selectedNote == null && noteCounts.isNotEmpty()) {
            selectedNote = noteCounts.first().first
        }
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
            // Header
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
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = if (selectedTab == HabitNoteTab.DONE) KairoSecondary else Color(0xFFFFB77D),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Notes & Reasons Analytics",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (totalNotesCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$totalNotesCount logs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // 2 Option Selectors (Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KairoSurfaceContainerHigh)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Done Notes Pill
                val isDoneSelected = selectedTab == HabitNoteTab.DONE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDoneSelected) KairoSecondary.copy(alpha = 0.22f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isDoneSelected) KairoSecondary else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            selectedTab = HabitNoteTab.DONE
                            selectedNote = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isDoneSelected) KairoSecondary else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Done Notes",
                            fontSize = 12.sp,
                            fontWeight = if (isDoneSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDoneSelected) KairoSecondary else Color(0xFF94A3B8)
                        )
                    }
                }

                // Not Done / Missed Reasons Pill
                val isMissedSelected = selectedTab == HabitNoteTab.MISSED
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isMissedSelected) Color(0xFFFFB77D).copy(alpha = 0.22f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isMissedSelected) Color(0xFFFFB77D) else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            selectedTab = HabitNoteTab.MISSED
                            selectedNote = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isMissedSelected) Color(0xFFFFB77D) else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Not Done Reasons",
                            fontSize = 12.sp,
                            fontWeight = if (isMissedSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMissedSelected) Color(0xFFFFB77D) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Notice for Yes/No habits when Done tab is selected
            if (habit.type == HabitType.YES_NO && selectedTab == HabitNoteTab.DONE) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = KairoPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Done notes apply to Target habits (e.g. pages read, glasses drank). Yes/No habits track \"Not Done Reasons\" to keep check-ins instant.",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (totalNotesCount == 0) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KairoSurfaceContainerHigh)
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (selectedTab == HabitNoteTab.DONE) "No completed notes logged yet" else "No missed reasons logged yet",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (selectedTab == HabitNoteTab.DONE) {
                                "Add quick notes when finishing target goals to see your progress insights here."
                            } else {
                                "When marking missed days, record quick reasons to identify what causes setbacks."
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // DONUT CHART + CENTER SUMMARY
                val sliceAngles = remember(noteCounts, totalNotesCount) {
                    var currentStart = -90f
                    noteCounts.mapIndexed { idx, (note, count) ->
                        val sweep = (count.toFloat() / totalNotesCount.toFloat()) * 360f
                        val color = colorPalette[idx % colorPalette.size]
                        val start = currentStart
                        currentStart += sweep
                        Triple(note, start to sweep, color)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(180.dp)
                            .pointerInput(sliceAngles) {
                                detectTapGestures { tapOffset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = tapOffset.x - cx
                                    val dy = tapOffset.y - cy
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val outerR = size.width / 2f
                                    val innerR = outerR - 40.dp.toPx()

                                    if (dist in innerR..outerR) {
                                        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        // Normalize so -90 deg is 0 deg
                                        angleDeg = (angleDeg + 90f + 360f) % 360f

                                        for ((note, span, _) in sliceAngles) {
                                            val (start, sweep) = span
                                            val normStart = (start + 90f + 360f) % 360f
                                            val normEnd = normStart + sweep
                                            if (sweep >= 360f || (angleDeg >= normStart && angleDeg < normEnd)) {
                                                selectedNote = note
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val strokeNormal = 22.dp.toPx()
                        val strokeSelected = 28.dp.toPx()
                        val diameter = size.minDimension - strokeSelected

                        sliceAngles.forEach { (note, span, color) ->
                            val (start, sweep) = span
                            val isSelected = note == selectedNote
                            val strokeWidth = if (isSelected) strokeSelected else strokeNormal

                            drawArc(
                                color = color,
                                startAngle = start,
                                sweepAngle = sweep.coerceAtLeast(1f),
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                topLeft = Offset(
                                    (size.width - diameter) / 2f,
                                    (size.height - diameter) / 2f
                                ),
                                size = Size(diameter, diameter)
                            )
                        }
                    }

                    // Center text in Donut Cutout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$totalNotesCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (selectedTab == HabitNoteTab.DONE) "Done Notes" else "Reasons",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // BREAKDOWN LIST & LEGEND
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Distribution (tap to inspect):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )

                    noteCounts.forEachIndexed { index, (note, count) ->
                        val color = colorPalette[index % colorPalette.size]
                        val isSelected = note == selectedNote
                        val pct = ((count.toFloat() / totalNotesCount.toFloat()) * 100).toInt()

                        Surface(
                            onClick = { selectedNote = note },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) color.copy(alpha = 0.16f) else KairoSurfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) color else Color.White.copy(alpha = 0.05f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = note,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$count time${if (count > 1) "s" else ""}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "$pct%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }

                // OCCURRENCES CARD (Requirement: on clicking a note, show the habits marked with that note)
                if (selectedNote != null) {
                    val activeNote = selectedNote!!
                    val noteColor = remember(activeNote, sliceAngles) {
                        sliceAngles.find { it.first == activeNote }?.third ?: KairoPrimary
                    }
                    val occurrences = remember(activeNote, targetState, allLogsMap) {
                        HabitRepository.getOccurrencesForNote(activeNote, targetState)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = KairoSurfaceContainerHighest,
                        border = androidx.compose.foundation.BorderStroke(1.dp, noteColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(noteColor)
                                    )
                                    Text(
                                        text = "\"$activeNote\"",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(noteColor.copy(alpha = 0.20f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${occurrences.size} total logs",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = noteColor
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text(
                                text = "Habits & Dates Marked With This Note:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8)
                            )

                            // Group occurrences by habit
                            val groupedByHabit = remember(occurrences) {
                                occurrences.groupBy { it.habitTitle }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                groupedByHabit.forEach { (hTitle, logsList) ->
                                    val isThisHabit = hTitle.equals(habit.title, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isThisHabit) KairoSurfaceContainerHigh else Color.White.copy(alpha = 0.03f))
                                            .border(
                                                1.dp,
                                                if (isThisHabit) noteColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                                        imageVector = Icons.Default.TaskAlt,
                                                        contentDescription = null,
                                                        tint = if (isThisHabit) noteColor else Color(0xFF94A3B8),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = if (isThisHabit) "$hTitle (This Habit)" else hTitle,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }

                                                Text(
                                                    text = "${logsList.size} log${if (logsList.size > 1) "s" else ""}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFCBD5E1)
                                                )
                                            }

                                            // Dates list
                                            val formattedDates = logsList.map { log ->
                                                val parsed = runCatching {
                                                    LocalDate.parse(log.date).format(DateTimeFormatter.ofPattern("MMM d"))
                                                }.getOrDefault(log.date)
                                                parsed
                                            }.joinToString(", ")

                                            Text(
                                                text = "Dates: $formattedDates",
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
