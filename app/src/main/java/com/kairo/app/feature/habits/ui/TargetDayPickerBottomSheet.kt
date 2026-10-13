package com.kairo.app.feature.habits.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayRecord
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TargetDayPickerBottomSheet(
    habit: Habit,
    date: LocalDate,
    initialRecord: HabitDayRecord,
    onDismiss: () -> Unit,
    onSave: (value: Int, state: HabitDayState, note: String?) -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val targetVal = habit.targetValue.coerceAtLeast(1)

    // Local mutable state for editing
    var currentValue by remember { mutableIntStateOf(initialRecord.value.coerceIn(0, targetVal)) }
    var textInput by remember { mutableStateOf(currentValue.toString()) }
    var currentState by remember {
        mutableStateOf(
            if (initialRecord.state == HabitDayState.MISSED) HabitDayState.MISSED
            else if (currentValue >= targetVal) HabitDayState.DONE
            else HabitDayState.UNMARKED
        )
    }

    // Split initial note into individual segments
    var noteSegments by remember {
        mutableStateOf(
            initialRecord.note
                ?.split(",", "\n", ";")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }
    var newNoteInput by remember { mutableStateOf("") }

    // Fetch recent note suggestions
    val recentNotes = remember(habit.id, currentState) {
        HabitRepository.getRecentNotes(habit.id, currentState)
    }

    // Synchronize textInput when currentValue changes from steppers
    LaunchedEffect(currentValue) {
        textInput = currentValue.toString()
        if (currentValue >= targetVal) {
            currentState = HabitDayState.DONE
        } else if (currentState == HabitDayState.DONE && currentValue < targetVal) {
            currentState = HabitDayState.UNMARKED
        }
    }

    val formattedDate = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault()))
    }

    val progressFraction = (currentValue.toFloat() / targetVal.toFloat()).coerceIn(0f, 1f)
    val isDone = currentState == HabitDayState.DONE || currentValue >= targetVal
    val isMissed = currentState == HabitDayState.MISSED

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
                        color = if (isDone) KairoSecondary else Color.White
                    )

                    // State Badge
                    val badgeColor = when {
                        isDone -> KairoSecondary
                        isMissed -> Color(0xFFFFB77D)
                        else -> Color(0xFF94A3B8)
                    }
                    val badgeText = when {
                        isDone -> "Goal Reached"
                        isMissed -> "Marked Missed"
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
                            .background(if (isDone) KairoSecondary else KairoPrimary)
                    )
                }
            }

            // Stepper & Manual Number Input Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHigh,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    currentValue -= 1
                                    // Smart LIFO: drop last note segment if notes exist
                                    if (noteSegments.isNotEmpty()) {
                                        noteSegments = noteSegments.dropLast(1)
                                    }
                                }
                            },
                            enabled = canDecrement,
                            shape = CircleShape,
                            color = if (canDecrement) KairoSurfaceContainerHighest else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (canDecrement) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = if (canDecrement) Color.White else Color.Gray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Manual Entry Input
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { input ->
                                val cleanInput = input.filter { it.isDigit() }
                                textInput = cleanInput
                                val parsed = cleanInput.toIntOrNull()
                                if (parsed != null) {
                                    val clamped = parsed.coerceIn(0, targetVal)
                                    currentValue = clamped
                                    if (clamped < noteSegments.size) {
                                        noteSegments = noteSegments.take(clamped)
                                    }
                                } else if (cleanInput.isEmpty()) {
                                    currentValue = 0
                                    noteSegments = emptyList()
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (isDone) KairoSecondary else Color.White
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier
                                .width(110.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                                focusedBorderColor = if (isDone) KairoSecondary else KairoPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                cursorColor = KairoPrimary
                            )
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // [+] Increment Button
                        val canIncrement = currentValue < targetVal
                        Surface(
                            onClick = {
                                if (canIncrement) {
                                    currentValue += 1
                                }
                            },
                            enabled = canIncrement,
                            shape = CircleShape,
                            color = if (canIncrement) KairoPrimary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (canIncrement) KairoPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.04f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = if (canIncrement) KairoPrimary else Color.Gray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Goal: $targetVal ${habit.targetUnit} daily (type any number directly)",
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
                        currentValue = 0
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

                // Clear
                Surface(
                    onClick = {
                        currentValue = 0
                        currentState = HabitDayState.UNMARKED
                        noteSegments = emptyList()
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

            // Notes Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMissed) "Missed Reason (Optional)" else "Notes & Breakdown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    if (noteSegments.isNotEmpty()) {
                        Text(
                            text = "${noteSegments.size} notes logged",
                            fontSize = 11.sp,
                            color = KairoSecondary
                        )
                    }
                }

                // Existing Note Chips
                if (noteSegments.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        noteSegments.forEachIndexed { index, segment ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = KairoSecondary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, KairoSecondary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = segment,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable {
                                                noteSegments = noteSegments.filterIndexed { i, _ -> i != index }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Note Input Field
                OutlinedTextField(
                    value = newNoteInput,
                    onValueChange = { newNoteInput = it },
                    placeholder = {
                        Text(
                            text = if (isMissed) "e.g. Travel, unwell, busy..." else "Add note (e.g. Fajr, chapter 2)...",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (newNoteInput.isNotBlank()) {
                            IconButton(onClick = {
                                val trimmed = newNoteInput.trim()
                                if (trimmed.isNotBlank()) {
                                    noteSegments = noteSegments + trimmed
                                    newNoteInput = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add note", tint = KairoPrimary)
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = newNoteInput.trim()
                        if (trimmed.isNotBlank()) {
                            noteSegments = noteSegments + trimmed
                            newNoteInput = ""
                        }
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.04f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.10f)
                    )
                )

                // Recent suggestions chips
                if (recentNotes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Recent:",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            recentNotes.take(4).forEach { suggestion ->
                                Surface(
                                    onClick = {
                                        if (!noteSegments.contains(suggestion)) {
                                            noteSegments = noteSegments + suggestion
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCBD5E1),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Save & Apply Button
            Button(
                onClick = {
                    val finalNote = if (newNoteInput.isNotBlank()) {
                        (noteSegments + newNoteInput.trim()).joinToString(", ")
                    } else if (noteSegments.isNotEmpty()) {
                        noteSegments.joinToString(", ")
                    } else {
                        null
                    }

                    val finalState = when {
                        currentState == HabitDayState.MISSED -> HabitDayState.MISSED
                        currentValue >= targetVal -> HabitDayState.DONE
                        else -> HabitDayState.UNMARKED
                    }

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
}
