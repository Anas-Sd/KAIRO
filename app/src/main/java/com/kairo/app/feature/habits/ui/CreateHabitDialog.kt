package com.kairo.app.feature.habits.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.ui.components.TonePickerDialog
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import java.util.Calendar
import java.util.Locale

@Composable
fun CreateHabitDialog(
    initialHabit: Habit? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        type: HabitType,
        targetValue: Int,
        targetUnit: String,
        repeatType: HabitRepeatType,
        repeatDays: List<Int>,
        weeklyTargetCount: Int,
        reminderEnabled: Boolean,
        reminderTime: String?,
        reminderToneUri: String?,
        reminderToneTitle: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(initialHabit?.title ?: "") }
    var habitType by remember { mutableStateOf(initialHabit?.type ?: HabitType.YES_NO) }
    var targetValueText by remember { mutableStateOf((initialHabit?.targetValue ?: 1).toString()) }
    var targetUnit by remember { mutableStateOf(initialHabit?.targetUnit ?: "") }

    var repeatType by remember { mutableStateOf(initialHabit?.repeatType ?: HabitRepeatType.EVERYDAY) }
    var selectedDays by remember { mutableStateOf(initialHabit?.repeatDays?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7)) } // 1=Mon .. 7=Sun
    var weeklyCount by remember { mutableIntStateOf(initialHabit?.weeklyTargetCount ?: 3) }

    var reminderEnabled by remember { mutableStateOf(initialHabit?.reminderEnabled ?: false) }
    var reminderTime by remember { mutableStateOf(initialHabit?.reminderTime ?: "08:00 AM") }
    var reminderToneUri by remember { mutableStateOf<String?>(initialHabit?.reminderToneUri) }
    var reminderToneTitle by remember { mutableStateOf(initialHabit?.reminderToneTitle ?: "Default Notification Tone") }
    var showTonePicker by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = KairoSurfaceContainer,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(18.dp)
            ) {
                // Modal Title
                Text(
                    text = if (initialHabit != null) "Edit Habit" else "New Habit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Habit Name Field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) errorMessage = null
                    },
                    label = { Text("Habit Name", fontSize = 13.sp) },
                    placeholder = { Text("e.g. Read Book, Drink Water", fontSize = 13.sp, color = Color.Gray) },
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedLabelColor = KairoPrimary,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Type: a. Yes/No  b. Target
                Text(
                    text = "TYPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypePill(
                        label = "✓ Yes / No",
                        isSelected = habitType == HabitType.YES_NO,
                        modifier = Modifier.weight(1f)
                    ) {
                        habitType = HabitType.YES_NO
                    }

                    TypePill(
                        label = "🔢 Target",
                        isSelected = habitType == HabitType.TARGET,
                        modifier = Modifier.weight(1f)
                    ) {
                        habitType = HabitType.TARGET
                    }
                }

                // If Target: show target value & unit inputs
                if (habitType == HabitType.TARGET) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Target Number Input (clean input without - and +)
                        OutlinedTextField(
                            value = targetValueText,
                            onValueChange = { newStr ->
                                if (newStr.all { it.isDigit() } && newStr.length <= 5) {
                                    targetValueText = newStr
                                }
                            },
                            label = { Text("Target", fontSize = 11.sp) },
                            placeholder = { Text("1", fontSize = 12.sp, color = Color.Gray) },
                            textStyle = TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KairoPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = KairoSurfaceContainerHigh,
                                unfocusedContainerColor = KairoSurfaceContainerHigh,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Right: Unit input (e.g. glasses, pages, km)
                        OutlinedTextField(
                            value = targetUnit,
                            onValueChange = { targetUnit = it },
                            label = { Text("Unit", fontSize = 11.sp) },
                            placeholder = { Text("e.g. glasses, reps", fontSize = 12.sp, color = Color.Gray) },
                            textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KairoPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = KairoSurfaceContainerHigh,
                                unfocusedContainerColor = KairoSurfaceContainerHigh,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Quick unit suggestion chips
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        val quickUnits = listOf("glasses", "pages", "km", "mins", "reps", "namaz")
                        quickUnits.forEach { unit ->
                            val isSelected = targetUnit.equals(unit, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) KairoPrimary.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) KairoPrimary else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        targetUnit = if (isSelected) "" else unit
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = unit,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) KairoPrimary else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Repeat: Everyday, Specific Days, Weekly X
                Text(
                    text = "REPEAT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TypePill(
                        label = "Everyday",
                        isSelected = repeatType == HabitRepeatType.EVERYDAY,
                        modifier = Modifier.weight(1f)
                    ) {
                        repeatType = HabitRepeatType.EVERYDAY
                        selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
                    }

                    TypePill(
                        label = "Specific Days",
                        isSelected = repeatType == HabitRepeatType.SPECIFIC_DAYS,
                        modifier = Modifier.weight(1.1f)
                    ) {
                        repeatType = HabitRepeatType.SPECIFIC_DAYS
                    }

                    TypePill(
                        label = "Weekly X",
                        isSelected = repeatType == HabitRepeatType.WEEKLY_X,
                        modifier = Modifier.weight(1f)
                    ) {
                        repeatType = HabitRepeatType.WEEKLY_X
                    }
                }

                // If Specific Days: show M T W T F S S chips
                if (repeatType == HabitRepeatType.SPECIFIC_DAYS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val dayLabels = listOf("M" to 1, "T" to 2, "W" to 3, "T" to 4, "F" to 5, "S" to 6, "S" to 7)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayLabels.forEach { (lbl, dayIdx) ->
                            val isDaySelected = selectedDays.contains(dayIdx)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isDaySelected) KairoSecondary else KairoSurfaceContainerHigh)
                                    .clickable {
                                        selectedDays = if (isDaySelected) {
                                            if (selectedDays.size > 1) selectedDays - dayIdx else selectedDays
                                        } else {
                                            selectedDays + dayIdx
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lbl,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDaySelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                // If Weekly X: show count stepper
                if (repeatType == HabitRepeatType.WEEKLY_X) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(KairoSurfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { if (weeklyCount > 1) weeklyCount-- },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "$weeklyCount times per week",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(14.dp))

                        IconButton(
                            onClick = { if (weeklyCount < 7) weeklyCount++ },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reminder (Optional)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (reminderEnabled) KairoPrimary else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Reminder Notification",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Notification alert with selected tone",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = KairoPrimaryContainer,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = KairoSurfaceContainerHigh
                        )
                    )
                }

                // If reminder enabled: Time Picker and Tone Picker
                if (reminderEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Time Picker Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KairoSurfaceContainerHigh)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .clickable {
                                val cal = Calendar.getInstance()
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val isPm = hourOfDay >= 12
                                        val hour12 = if (hourOfDay == 0) 12 else if (hourOfDay > 12) hourOfDay - 12 else hourOfDay
                                        val amPm = if (isPm) "PM" else "AM"
                                        reminderTime = String.format(Locale.US, "%02d:%02d %s", hour12, minute, amPm)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reminderTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Tone Picker Button
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KairoSurfaceContainerHigh)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .clickable { showTonePicker = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = KairoSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reminderToneTitle.take(16) + (if (reminderToneTitle.length > 16) "..." else ""),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel / Create
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "Please enter a habit name"
                                return@Button
                            }
                            val finalTarget = if (habitType == HabitType.TARGET) {
                                targetValueText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            } else {
                                1
                            }
                            onConfirm(
                                title.trim(),
                                habitType,
                                finalTarget,
                                targetUnit.trim(),
                                repeatType,
                                selectedDays.toList().sorted(),
                                weeklyCount,
                                reminderEnabled,
                                if (reminderEnabled) reminderTime else null,
                                if (reminderEnabled) reminderToneUri else null,
                                if (reminderEnabled) reminderToneTitle else null
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KairoPrimaryContainer),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            text = if (initialHabit != null) "Save Changes" else "Create Habit",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Tone Picker Dialog
    if (showTonePicker) {
        TonePickerDialog(
            initialUri = reminderToneUri,
            initialTitle = reminderToneTitle,
            onDismiss = { showTonePicker = false },
            onConfirm = { uri, toneTitle ->
                reminderToneUri = uri
                reminderToneTitle = toneTitle
                showTonePicker = false
            }
        )
    }
}

@Composable
private fun TypePill(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) KairoPrimary.copy(alpha = 0.18f) else KairoSurfaceContainerHigh)
            .border(
                1.dp,
                if (isSelected) KairoPrimary else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) KairoPrimary else Color(0xFFCBD5E1)
        )
    }
}
