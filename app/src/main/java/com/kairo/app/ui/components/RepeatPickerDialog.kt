package com.kairo.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest
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
        mutableIntStateOf(if (initialRepeatType == "DATES") 1 else 0)
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
    val currentSummary = remember(selectedDays.toList(), selectedDates.toList()) {
        val daysSummary = if (selectedDays.size == 7) "Everyday"
        else if (selectedDays.size == 5 && !selectedDays.contains("SAT") && !selectedDays.contains("SUN")) "Weekdays"
        else if (selectedDays.size == 2 && selectedDays.contains("SAT") && selectedDays.contains("SUN")) "Weekends"
        else if (selectedDays.isNotEmpty()) "Every " + selectedDays.joinToString(", ") { it.take(3) }
        else null

        val datesSummary = if (selectedDates.size == 1) selectedDates.first()
        else if (selectedDates.isNotEmpty()) "${selectedDates.size} specific dates"
        else null

        when {
            daysSummary != null && datesSummary != null -> "$daysSummary + $datesSummary"
            daysSummary != null -> daysSummary
            datesSummary != null -> datesSummary
            else -> "Does not repeat"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = KairoSurfaceContainerLowest,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f)),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(KairoPrimary.copy(alpha = 0.2f), CircleShape),
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
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFC8C4D9)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs: Days of Week vs Specific Dates (Both can be configured together!)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = KairoSurfaceContainerHighest.copy(alpha = 0.4f),
                    contentColor = KairoPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = KairoPrimary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            val badge = if (selectedDays.isNotEmpty()) " (${selectedDays.size})" else ""
                            Text(
                                "Days of Week$badge",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) Color.White else Color(0xFF918EA2)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val badge = if (selectedDates.isNotEmpty()) " (${selectedDates.size})" else ""
                            Text(
                                "Specific Dates$badge",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) Color.White else Color(0xFF918EA2)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (selectedTab == 0) {
                    // TAB 1: DAYS OF WEEK
                    Text(
                        text = "Repeat on specific days:",
                        fontSize = 13.sp,
                        color = Color(0xFFC8C4D9),
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
                                    .size(38.dp)
                                    .background(
                                        if (isSelected) KairoPrimary else KairoSurfaceContainerHighest.copy(alpha = 0.4f),
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
                                    color = if (isSelected) Color(0xFF130067) else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Quick Preset Buttons with balanced, comfortable padding
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
                        text = "Pick specific dates to repeat:",
                        fontSize = 13.sp,
                        color = Color(0xFFC8C4D9),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedDates.forEach { dateStr ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = KairoPrimary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.6f))
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
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFC8C4D9),
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
                            color = KairoSurfaceContainerHighest.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { openDatePicker() }
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
                                    fontWeight = FontWeight.Medium,
                                    color = KairoPrimary
                                )
                            }
                        }
                    }

                    if (selectedDates.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No dates added yet. Tap 'Add Date' to select specific reminder dates.",
                            fontSize = 12.sp,
                            color = Color(0xFF6B687C)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Schedule Live Indicator (combines both days & specific dates)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Reset & Confirm
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text("No Repeat", color = Color(0xFFC8C4D9))
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

                            val datesSummary = if (selectedDates.size == 1) selectedDates.first()
                            else if (selectedDates.isNotEmpty()) "${selectedDates.size} specific dates"
                            else null

                            val result = when {
                                hasDays && hasDates -> {
                                    RepeatResult("BOTH", selectedDays.joinToString(","), selectedDates.joinToString(","), "$daysSummary + $datesSummary")
                                }
                                hasDays -> {
                                    RepeatResult("DAYS", selectedDays.joinToString(","), null, daysSummary!!)
                                }
                                hasDates -> {
                                    RepeatResult("DATES", null, selectedDates.joinToString(","), datesSummary!!)
                                }
                                else -> {
                                    RepeatResult(null, null, null, "Does not repeat")
                                }
                            }
                            onConfirm(result.type, result.days, result.dates, result.summary)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary)
                    ) {
                        Text("Confirm", color = Color(0xFF130067), fontWeight = FontWeight.Bold)
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
        color = KairoSurfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 9.dp),
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
