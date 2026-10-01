package com.kairo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.DateFilter
import com.kairo.app.data.model.FilterCriteria
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.StatusFilter
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    initialFilter: FilterCriteria,
    onApplyFilter: (FilterCriteria) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var tempStatuses by remember { mutableStateOf(initialFilter.selectedStatuses) }
    var tempPriorities by remember { mutableStateOf(initialFilter.selectedPriorities) }
    var tempDate by remember { mutableStateOf(initialFilter.selectedDate) }
    var tempRangeStart by remember { mutableStateOf(initialFilter.dateRangeStart) }
    var tempRangeEnd by remember { mutableStateOf(initialFilter.dateRangeEnd) }

    var showRangePickerDialog by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = tempRangeStart,
        initialSelectedEndDateMillis = tempRangeEnd
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KairoSurfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(KairoOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header Row: Title & Reset Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Tasks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Reset button: clears filters AND closes the bottom sheet
                Text(
                    text = "Reset",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = KairoPrimary,
                    modifier = Modifier
                        .clickable {
                            onApplyFilter(FilterCriteria())
                            onDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. STATUS SECTION
            SectionTitle(title = "STATUS")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusFilter.values().forEach { status ->
                    val isSelected = status in tempStatuses
                    FilterChip(
                        label = status.label,
                        isSelected = isSelected,
                        onClick = {
                            tempStatuses = if (isSelected) {
                                tempStatuses - status
                            } else {
                                tempStatuses + status
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. PRIORITY SECTION
            SectionTitle(title = "PRIORITY")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.URGENT).forEach { priority ->
                    val label = when (priority) {
                        Priority.LOW -> "Low"
                        Priority.MEDIUM -> "Med"
                        Priority.HIGH -> "High"
                        Priority.URGENT -> "Urgent"
                    }
                    val isSelected = priority in tempPriorities
                    FilterChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            tempPriorities = if (isSelected) {
                                tempPriorities - priority
                            } else {
                                tempPriorities + priority
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. DUE DATE SECTION
            SectionTitle(title = "DUE DATE")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Today chip
                FilterChip(
                    label = "Today",
                    isSelected = tempDate == DateFilter.TODAY,
                    onClick = {
                        tempDate = if (tempDate == DateFilter.TODAY) null else DateFilter.TODAY
                    }
                )

                // Tomorrow chip
                FilterChip(
                    label = "Tomorrow",
                    isSelected = tempDate == DateFilter.TOMORROW,
                    onClick = {
                        tempDate = if (tempDate == DateFilter.TOMORROW) null else DateFilter.TOMORROW
                    }
                )

                // Range chip (opens DateRangePicker on click)
                val rangeLabel = if (tempDate == DateFilter.RANGE && tempRangeStart != null && tempRangeEnd != null) {
                    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                    "${fmt.format(Date(tempRangeStart!!))} - ${fmt.format(Date(tempRangeEnd!!))}"
                } else {
                    "Range"
                }

                FilterChip(
                    label = rangeLabel,
                    isSelected = tempDate == DateFilter.RANGE,
                    onClick = {
                        showRangePickerDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // 4. APPLY FILTERS BUTTON
            Button(
                onClick = {
                    onApplyFilter(
                        FilterCriteria(
                            selectedStatuses = tempStatuses,
                            selectedPriorities = tempPriorities,
                            selectedDate = tempDate,
                            dateRangeStart = tempRangeStart,
                            dateRangeEnd = tempRangeEnd
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoPrimaryContainer,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Apply Filters",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Material 3 Date Range Picker Dialog
    if (showRangePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showRangePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            tempRangeStart = start
                            tempRangeEnd = end
                            tempDate = DateFilter.RANGE
                        }
                        showRangePickerDialog = false
                    }
                ) {
                    Text("Confirm", color = KairoPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRangePickerDialog = false }
                ) {
                    Text("Cancel", color = Color(0xFFC8C4D9))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = KairoSurfaceContainerHigh
            )
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f),
                colors = DatePickerDefaults.colors(
                    containerColor = KairoSurfaceContainerHigh,
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color(0xFF918EA2),
                    subheadContentColor = Color.White,
                    yearContentColor = Color.White,
                    currentYearContentColor = KairoPrimary,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = KairoPrimaryContainer,
                    dayContentColor = Color.White,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = KairoPrimaryContainer,
                    todayContentColor = KairoPrimary,
                    todayDateBorderColor = KairoPrimary,
                    dayInSelectionRangeContentColor = Color.White,
                    dayInSelectionRangeContainerColor = KairoPrimaryContainer.copy(alpha = 0.35f)
                )
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Color(0xFF918EA2)
    )
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) KairoPrimaryContainer else KairoSurfaceContainerHighest
    val textColor = if (isSelected) Color.White else Color(0xFFC8C4D9)
    val borderColor = if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
