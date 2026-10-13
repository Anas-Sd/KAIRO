package com.kairo.app.feature.habits.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayRecord
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitNoteHelper
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Micro Value Picker for past and target habit dates (Pattern 2).
 * Strictly stepper-based (no direct numeric typing) with hashmap notes integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetDayPickerBottomSheet(
    habit: Habit,
    date: LocalDate,
    initialRecord: HabitDayRecord,
    onDismiss: () -> Unit,
    onSave: (value: Int, state: HabitDayState, note: String?) -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val targetVal = habit.targetValue.coerceAtLeast(1)
    val isPastDay = date.isBefore(LocalDate.now())

    // Local mutable state for editing
    var currentValue by remember { mutableIntStateOf(initialRecord.value.coerceIn(0, targetVal)) }
    var currentState by remember {
        mutableStateOf(
            if (isPastDay && initialRecord.value < targetVal) HabitDayState.MISSED
            else if (initialRecord.state == HabitDayState.MISSED) HabitDayState.MISSED
            else if (initialRecord.value >= targetVal) HabitDayState.DONE
            else HabitDayState.UNMARKED
        )
    }

    // Hashmap storing 1-indexed count -> note
    val notesMap = remember {
        val initialMap = HabitNoteHelper.parseIncrementNotes(initialRecord.note)
        mutableStateMapOf<Int, String>().apply { putAll(initialMap) }
    }

    // Active increment note bottomsheet state
    var activeNoteCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(currentValue) {
        if (currentValue >= targetVal) {
            currentState = HabitDayState.DONE
        } else if (isPastDay) {
            currentState = HabitDayState.MISSED
        } else if (currentState == HabitDayState.DONE && currentValue < targetVal) {
            currentState = HabitDayState.UNMARKED
        }
    }

    val formattedDate = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault()))
    }

    val progressFraction = (currentValue.toFloat() / targetVal.toFloat()).coerceIn(0f, 1f)
    val isDone = currentState == HabitDayState.DONE || currentValue >= targetVal
    val isMissed = (isPastDay && currentValue < targetVal) || currentState == HabitDayState.MISSED

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KairoSurfaceContainer,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title, Date & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Progress Summary Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$currentValue / $targetVal ${habit.targetUnit}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) KairoSecondary else if (isMissed) Color(0xFFFFB77D) else Color.White
                    )

                    // State Badge
                    val badgeColor = when {
                        isDone -> KairoSecondary
                        isMissed -> Color(0xFFFFB77D)
                        currentValue > 0 -> Color(0xFFFACC15)
                        else -> Color(0xFF94A3B8)
                    }
                    val badgeText = when {
                        isDone -> "Goal Reached"
                        isMissed -> if (currentValue > 0) "Missed (${currentValue}✕)" else "Missed"
                        currentValue > 0 -> "${(progressFraction * 100).toInt()}% Done"
                        else -> "Unmarked"
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Mini Progress Fill Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressFraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDone) KairoSecondary else if (isMissed) Color(0xFFFFB77D) else if (currentValue > 0) Color(0xFFFACC15) else KairoPrimary)
                    )
                }
            }

            // Stepper Card (No Direct Numeric Input: Strict Stepping Only)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHigh,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Adjust Count for This Day",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // [-] Decrement Button
                        val canDecrement = currentValue > 0
                        Surface(
                            onClick = {
                                if (canDecrement) {
                                    // Remove note for the decremented count index only
                                    notesMap.remove(currentValue)
                                    currentValue -= 1
                                }
                            },
                            enabled = canDecrement,
                            shape = CircleShape,
                            color = if (canDecrement) KairoSurfaceContainerHighest else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(
                                1.dp,
                                if (canDecrement) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = if (canDecrement) Color.White else Color.Gray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        // Large Clean Value Display (Text Only - No Direct Typing)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$currentValue",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDone) KairoSecondary else if (isMissed) Color(0xFFFFB77D) else Color.White
                            )
                            if (habit.targetUnit.isNotBlank()) {
                                Text(
                                    text = habit.targetUnit,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        // [+] Increment Button (Increments & Prompts Note BottomSheet)
                        val canIncrement = currentValue < targetVal
                        Surface(
                            onClick = {
                                if (canIncrement) {
                                    val nextVal = currentValue + 1
                                    currentValue = nextVal
                                    // Open increment note bottomsheet for this count
                                    activeNoteCount = nextVal
                                }
                            },
                            enabled = canIncrement,
                            shape = CircleShape,
                            color = if (canIncrement) KairoPrimary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(
                                1.dp,
                                if (canIncrement) KairoPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.04f)
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = if (canIncrement) KairoPrimary else Color.Gray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Goal: $targetVal ${habit.targetUnit} daily (tap + to increment & note)",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Quick Status Actions: Done, Missed, Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Done (Goal)
                Surface(
                    onClick = {
                        currentValue = targetVal
                        currentState = HabitDayState.DONE
                        activeNoteCount = targetVal
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDone) KairoSecondary else KairoSurfaceContainerHigh,
                    border = BorderStroke(1.dp, if (isDone) KairoSecondary else Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isDone) Color(0xFF003822) else KairoSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Done ($targetVal)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDone) Color(0xFF003822) else KairoSecondary
                        )
                    }
                }

                // Missed
                Surface(
                    onClick = {
                        currentState = HabitDayState.MISSED
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isMissed) Color(0xFFFFB77D) else KairoSurfaceContainerHigh,
                    border = BorderStroke(1.dp, if (isMissed) Color(0xFFFFB77D) else Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isMissed) Color(0xFF4E2600) else Color(0xFFFFB77D),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Missed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMissed) Color(0xFF4E2600) else Color(0xFFFFB77D)
                        )
                    }
                }

                // Clear (Only for today — past days only have Done / Missed)
                if (!isPastDay) {
                    Surface(
                        onClick = {
                            currentValue = 0
                            currentState = HabitDayState.UNMARKED
                            notesMap.clear()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = KairoSurfaceContainerHigh,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
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

            Spacer(modifier = Modifier.height(4.dp))

            // Save & Apply Button
            Button(
                onClick = {
                    val finalState = when {
                        currentValue >= targetVal -> HabitDayState.DONE
                        isPastDay -> HabitDayState.MISSED
                        currentState == HabitDayState.MISSED -> HabitDayState.MISSED
                        currentValue == 0 -> HabitDayState.UNMARKED
                        else -> HabitDayState.UNMARKED
                    }

                    val finalNote = HabitNoteHelper.serializeIncrementNotes(notesMap)
                    onSave(currentValue, finalState, finalNote)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDone) KairoSecondary else KairoPrimary
                )
            ) {
                Text(
                    text = "Save Progress",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) Color(0xFF003822) else Color.White
                )
            }
        }
    }

    // Secondary Sheet for increment notes
    activeNoteCount?.let { countIdx ->
        HabitIncrementNoteBottomSheet(
            habitTitle = habit.title,
            habitId = habit.id,
            countIndex = countIdx,
            targetValue = targetVal,
            targetUnit = habit.targetUnit,
            isMissed = false,
            initialNote = notesMap[countIdx],
            onDismiss = { activeNoteCount = null },
            onSaveNote = { note ->
                if (note != null) {
                    notesMap[countIdx] = note
                } else {
                    notesMap.remove(countIdx)
                }
                activeNoteCount = null
            }
        )
    }
}
