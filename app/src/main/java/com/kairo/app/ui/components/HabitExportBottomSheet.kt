package com.kairo.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.export.HabitExportFormat
import com.kairo.app.feature.habits.export.HabitExportHelper
import com.kairo.app.feature.habits.export.HabitExportTimeline
import com.kairo.app.feature.habits.model.DisciplineOverviewStats
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.theme.KairoOnPrimary
import com.kairo.app.ui.theme.KairoOnSurface
import com.kairo.app.ui.theme.KairoOnSurfaceVariant
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitExportBottomSheet(
    habitsToExport: List<HabitWithStats>,
    overallStats: DisciplineOverviewStats? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFormat by remember { mutableStateOf(HabitExportFormat.HUMAN_READABLE) }
    var selectedTimeline by remember { mutableStateOf(HabitExportTimeline.ALL_TIME) }
    var customStartDate by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var customEndDate by remember { mutableStateOf(LocalDate.now()) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = KairoSurfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(KairoPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Export Habits",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = KairoOnSurface
                        )
                        Text(
                            text = "Save to Downloads & Share",
                            fontSize = 13.sp,
                            color = KairoOnSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = KairoOnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Scope Information Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHigh,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KairoSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = KairoSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (habitsToExport.size == 1) {
                                "Exporting \"${habitsToExport.first().habit.title}\""
                            } else {
                                "Exporting ${habitsToExport.size} Habits"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KairoOnSurface
                        )
                        Text(
                            text = if (habitsToExport.size == 1) {
                                val h = habitsToExport.first()
                                "Current Streak: ${h.streak} | Adherence: ${h.adherenceRate.toInt()}% | Full logs & notes"
                            } else {
                                "${habitsToExport.size} habit(s) with streaks, logs, and notes."
                            },
                            fontSize = 12.sp,
                            color = KairoOnSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. TIMELINE SELECTOR
            Text(
                text = "SELECT TIMELINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = KairoPrimary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Timeline Horizontal Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HabitExportTimeline.values().forEach { timeline ->
                    val isSelected = selectedTimeline == timeline
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTimeline = timeline },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) KairoPrimaryContainer.copy(alpha = 0.25f) else KairoSurfaceContainerLowest,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Text(
                            text = timeline.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) KairoPrimary else KairoOnSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // If Custom Range is selected: Show Start and End Date pickers
            if (selectedTimeline == HabitExportTimeline.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Date Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        customStartDate = LocalDate.of(year, month + 1, dayOfMonth)
                                    },
                                    customStartDate.year,
                                    customStartDate.monthValue - 1,
                                    customStartDate.dayOfMonth
                                ).show()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = KairoSurfaceContainerLowest,
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(text = "From", fontSize = 10.sp, color = KairoOnSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = customStartDate.format(dateFormatter),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KairoOnSurface
                                )
                            }
                        }
                    }

                    // End Date Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        customEndDate = LocalDate.of(year, month + 1, dayOfMonth)
                                    },
                                    customEndDate.year,
                                    customEndDate.monthValue - 1,
                                    customEndDate.dayOfMonth
                                ).show()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = KairoSurfaceContainerLowest,
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(text = "To", fontSize = 10.sp, color = KairoOnSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = KairoSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = customEndDate.format(dateFormatter),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KairoOnSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. FORMAT SELECTOR
            Text(
                text = "SELECT EXPORT FORMAT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = KairoPrimary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Option 1: Human Readable (.txt)
            HabitExportOptionCard(
                title = "Detailed Report (.txt)",
                subtitle = "Overview report with streaks, logs, and notes",
                icon = Icons.Default.Description,
                isSelected = selectedFormat == HabitExportFormat.HUMAN_READABLE,
                onClick = { selectedFormat = HabitExportFormat.HUMAN_READABLE }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Option 2: CSV Spreadsheet (.csv)
            HabitExportOptionCard(
                title = "Spreadsheet (.csv)",
                subtitle = "Compatible with Excel and Google Sheets",
                icon = Icons.Default.TableChart,
                isSelected = selectedFormat == HabitExportFormat.CSV,
                onClick = { selectedFormat = HabitExportFormat.CSV }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Export & Share Action Button
            Button(
                onClick = {
                    val success = HabitExportHelper.exportAndShare(
                        context = context,
                        format = selectedFormat,
                        timeline = selectedTimeline,
                        habits = habitsToExport,
                        overallStats = overallStats,
                        customStartDate = if (selectedTimeline == HabitExportTimeline.CUSTOM) customStartDate else null,
                        customEndDate = if (selectedTimeline == HabitExportTimeline.CUSTOM) customEndDate else null
                    )
                    if (success) {
                        onDismissRequest()
                    }
                },
                enabled = habitsToExport.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoPrimary,
                    contentColor = KairoOnPrimary,
                    disabledContainerColor = KairoSurfaceContainerHigh,
                    disabledContentColor = KairoOnSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (habitsToExport.isNotEmpty()) {
                            "Export & Share"
                        } else {
                            "No Habits to Export"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitExportOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) KairoPrimaryContainer.copy(alpha = 0.18f) else KairoSurfaceContainerLowest,
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) KairoPrimary.copy(alpha = 0.25f) else KairoSurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) KairoPrimary else KairoOnSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) KairoPrimary else KairoOnSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = KairoOnSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) KairoPrimary else KairoOnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
