package com.kairo.app.ui.components

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val ALL_WEEK_DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
val DAY_LABELS = mapOf(
    "MON" to "M",
    "TUE" to "T",
    "WED" to "W",
    "THU" to "T",
    "FRI" to "F",
    "SAT" to "S",
    "SUN" to "S"
)

private data class RepeatResult(
    val type: String?,
    val days: String?,
    val dates: String?,
    val summary: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatPickerDialog(
    initialRepeatType: String?, // "NONE", "DAYS", "DATES", "BOTH"
    initialRepeatDays: String?, // "MON,WED"
    initialRepeatDates: String?, // "Oct 5, Oct 12"
    onDismiss: () -> Unit,
    onConfirm: (repeatType: String?, repeatDays: String?, repeatDates: String?, summary: String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember {
        mutableIntStateOf(if (initialRepeatType == "DATES" || initialRepeatType == "MONTHLY_DATES") 1 else 0)
    }

    var dateRepeatMode by remember {
        mutableStateOf(if (initialRepeatType == "MONTHLY_DATES" || initialRepeatType == "BOTH_MONTHLY") "EVERY" else "ONLY_NOW")
    }

    // Days selection state
    val selectedDays = remember {
        mutableStateListOf<String>().apply {
            if (!initialRepeatDays.isNullOrBlank()) {
                addAll(initialRepeatDays.split(",").map { it.trim() }.filter { it.isNotBlank() })
            }
        }
    }

    // Specific dates selection state
    val selectedDates = remember {
        mutableStateListOf<String>().apply {
            if (!initialRepeatDates.isNullOrBlank()) {
                addAll(initialRepeatDates.split(",").map { it.trim() }.filter { it.isNotBlank() })
            }
        }
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val fmt = SimpleDateFormat("MMM d", Locale.getDefault()).format(chosen.time)
                if (!selectedDates.contains(fmt)) {
                    selectedDates.add(fmt)
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Live combined schedule summary
    val currentSummary = remember(selectedDays.toList(), selectedDates.toList(), dateRepeatMode) {
        val daysSummary = if (selectedDays.size == 7) "Everyday"
        else if (selectedDays.size == 5 && !selectedDays.contains("SAT") && !selectedDays.contains("SUN")) "Weekdays"
        else if (selectedDays.size == 2 && selectedDays.contains("SAT") && selectedDays.contains("SUN")) "Weekends"
        else if (selectedDays.isNotEmpty()) "Every " + selectedDays.joinToString(", ") { it.take(3) }
        else null

        val datesSummary = if (selectedDates.isEmpty()) null
        else {
            val base = if (selectedDates.size == 1) selectedDates.first() else "${selectedDates.size} specific dates"
            if (dateRepeatMode == "EVERY") "Every month on $base" else "$base (only for now)"
        }

        when {
            daysSummary != null && datesSummary != null -> "$daysSummary + $datesSummary"
            daysSummary != null -> daysSummary
            datesSummary != null -> datesSummary
            else -> "Does not repeat"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = KairoSurfaceContainer,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KairoPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Repeat Task",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Segmented Tab Switcher (Days of Week vs Specific Dates)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val daysBadge = if (selectedDays.isNotEmpty()) " (${selectedDays.size})" else ""
                        val datesBadge = if (selectedDates.isNotEmpty()) " (${selectedDates.size})" else ""

                        // Tab 0: Days of Week
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selectedTab == 0) KairoPrimary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable { selectedTab = 0 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Days of Week$daysBadge",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Tab 1: Specific Dates
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selectedTab == 1) KairoPrimary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable { selectedTab = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Specific Dates$datesBadge",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // TAB 1: DAYS OF WEEK
                    Text(
                        text = "Repeat on specific days:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Day Circles: [M] [T] [W] [T] [F] [S] [S]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ALL_WEEK_DAYS.forEach { dayKey ->
                            val isSelected = selectedDays.contains(dayKey)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) KairoPrimary else KairoSurfaceContainerLow
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.25f),
                                        CircleShape
                                    )
                                    .clickable {
                                        if (isSelected) selectedDays.remove(dayKey)
                                        else selectedDays.add(dayKey)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = DAY_LABELS[dayKey] ?: dayKey.take(1),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF1E1F26) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Preset Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetChip(
                            label = "Everyday",
                            color = KairoPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedDays.clear()
                                selectedDays.addAll(ALL_WEEK_DAYS)
                            }
                        )

                        PresetChip(
                            label = "Weekdays",
                            color = KairoSecondary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedDays.clear()
                                selectedDays.addAll(listOf("MON", "TUE", "WED", "THU", "FRI"))
                            }
                        )

                        PresetChip(
                            label = "Weekends",
                            color = Color(0xFFFFB77D),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedDays.clear()
                                selectedDays.addAll(listOf("SAT", "SUN"))
                            }
                        )

                        PresetChip(
                            label = "Clear",
                            color = Color(0xFFFFB4AB),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedDays.clear()
                            }
                        )
                    }
                } else {
                    // TAB 2: SPECIFIC DATES
                    Text(
                        text = "Recurrence mode on specified dates:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2 selectable options: Every specified date vs Only for now
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isEverySelected = dateRepeatMode == "EVERY"
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { dateRepeatMode = "EVERY" },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isEverySelected) KairoPrimary.copy(alpha = 0.12f) else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isEverySelected) KairoPrimary.copy(alpha = 0.5f)
                                else KairoOutlineVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Every specified date",
                                    fontSize = 12.sp,
                                    fontWeight = if (isEverySelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isEverySelected) KairoPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Repeats every month",
                                    fontSize = 10.sp,
                                    color = if (isEverySelected) KairoPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val isOnlyNowSelected = dateRepeatMode == "ONLY_NOW"
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { dateRepeatMode = "ONLY_NOW" },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isOnlyNowSelected) KairoPrimary.copy(alpha = 0.12f) else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isOnlyNowSelected) KairoPrimary.copy(alpha = 0.5f)
                                else KairoOutlineVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Only for now",
                                    fontSize = 12.sp,
                                    fontWeight = if (isOnlyNowSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isOnlyNowSelected) KairoPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "One-time execution",
                                    fontSize = 10.sp,
                                    color = if (isOnlyNowSelected) KairoPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (dateRepeatMode == "EVERY") "Select date(s) of month:" else "Pick specific dates to alert:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedDates.forEach { dateStr ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = KairoPrimary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = KairoPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = dateStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { selectedDates.remove(dateStr) }
                                    )
                                }
                            }
                        }

                        // "+ Add Date" Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KairoSurfaceContainerLow,
                            border = BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { openDatePicker() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add date",
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Add Date",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KairoPrimary
                                )
                            }
                        }
                    }

                    if (selectedDates.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No dates added yet. Tap 'Add Date' to select specific reminder dates.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Schedule Live Indicator
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Schedule: $currentSummary",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: No Repeat & Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedDays.clear()
                            selectedDates.clear()
                            onConfirm(null, null, null, "Does not repeat")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "No Repeat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            val hasDays = selectedDays.isNotEmpty()
                            val hasDates = selectedDates.isNotEmpty()

                            val daysSummary = if (selectedDays.size == 7) "Everyday"
                            else if (selectedDays.size == 5 && !selectedDays.contains("SAT") && !selectedDays.contains("SUN")) "Weekdays"
                            else if (selectedDays.size == 2 && selectedDays.contains("SAT") && selectedDays.contains("SUN")) "Weekends"
                            else if (selectedDays.isNotEmpty()) "Every " + selectedDays.joinToString(", ") { it.take(3) }
                            else null

                            val datesSummary = if (selectedDates.isEmpty()) null
                            else {
                                val base = if (selectedDates.size == 1) selectedDates.first() else "${selectedDates.size} specific dates"
                                if (dateRepeatMode == "EVERY") "Every month on $base" else "$base (only for now)"
                            }

                            val result = when {
                                hasDays && hasDates -> {
                                    val type = if (dateRepeatMode == "EVERY") "BOTH_MONTHLY" else "BOTH"
                                    RepeatResult(type, selectedDays.joinToString(","), selectedDates.joinToString(","), "$daysSummary + $datesSummary")
                                }
                                hasDays -> {
                                    RepeatResult("DAYS", selectedDays.joinToString(","), null, daysSummary!!)
                                }
                                hasDates -> {
                                    val type = if (dateRepeatMode == "EVERY") "MONTHLY_DATES" else "DATES"
                                    RepeatResult(type, null, selectedDates.joinToString(","), datesSummary!!)
                                }
                                else -> {
                                    RepeatResult(null, null, null, "Does not repeat")
                                }
                            }
                            onConfirm(result.type, result.days, result.dates, result.summary)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KairoPrimary,
                            contentColor = Color(0xFF1E1F26)
                        )
                    ) {
                        Text(
                            text = "Confirm",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = KairoSurfaceContainerLow,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
