package com.kairo.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kairo.app.data.model.Priority
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        notes: String?,
        priority: Priority,
        dueDate: String,
        dueTime: String?,
        location: String?,
        attachmentName: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var priorityMenuExpanded by remember { mutableStateOf(false) }

    // Date & Time state
    var selectedDateText by remember { mutableStateOf("Today") }
    var selectedTimeText by remember {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        mutableStateOf(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
    }

    var locationText by remember { mutableStateOf("") }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    var showAttachmentSourceDialog by remember { mutableStateOf(false) }

    // Native Date & Time picker launchers
    fun showDateTimePicker() {
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                selectedDateText = fmt.format(chosenCal.time)

                // Follow up with Time Picker
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        selectedTimeText = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            currentYear,
            currentMonth,
            currentDay
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = KairoSurfaceContainerLowest,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Task title & Close (X) button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Task",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFC8C4D9),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Task Name Input Box
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Task name", color = Color(0xFF918EA2)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.35f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f),
                        unfocusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Notes Input Box (optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Notes", color = Color(0xFF918EA2)) },
                    minLines = 2,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.35f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f),
                        unfocusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Side-by-side Row: Priority Dropdown + Date & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Priority selector box with dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clickable { priorityMenuExpanded = true },
                            shape = RoundedCornerShape(14.dp),
                            color = KairoSurfaceContainerHighest.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val dotColor = when (selectedPriority) {
                                        Priority.LOW -> KairoSecondary
                                        Priority.MEDIUM -> KairoPrimary
                                        Priority.HIGH -> Color(0xFFFFB77D)
                                        Priority.URGENT -> KairoError
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(dotColor, RoundedCornerShape(100.dp))
                                    )
                                    Text(
                                        text = selectedPriority.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color(0xFFC8C4D9),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = priorityMenuExpanded,
                            onDismissRequest = { priorityMenuExpanded = false }
                        ) {
                            listOf(Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.URGENT).forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = p.label,
                                            fontWeight = if (p == selectedPriority) FontWeight.Bold else FontWeight.Normal,
                                            color = if (p == selectedPriority) KairoPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedPriority = p
                                        priorityMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Date & Time Picker trigger
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .clickable { showDateTimePicker() },
                        shape = RoundedCornerShape(14.dp),
                        color = KairoSurfaceContainerHighest.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Date and Time",
                                tint = KairoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$selectedDateText, $selectedTimeText",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Location Input Box (optional)
                OutlinedTextField(
                    value = locationText,
                    onValueChange = { locationText = it },
                    placeholder = { Text("Location", color = Color(0xFF918EA2)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Location",
                            tint = Color(0xFF918EA2),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.35f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f),
                        unfocusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Files / Photos selector
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable { showAttachmentSourceDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    color = KairoSurfaceContainerHighest.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Files",
                                tint = if (attachmentName != null) KairoPrimary else Color(0xFF918EA2),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = attachmentName ?: "Files",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (attachmentName != null) Color.White else Color(0xFF918EA2)
                            )
                        }

                        if (attachmentName != null) {
                            IconButton(
                                onClick = { attachmentName = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFC8C4D9),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 6. Action Buttons: Cancel and Create
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFFC8C4D9),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            onConfirm(
                                title.trim(),
                                if (notes.isNotBlank()) notes.trim() else null,
                                selectedPriority,
                                selectedDateText,
                                selectedTimeText,
                                if (locationText.isNotBlank()) locationText.trim() else null,
                                attachmentName
                            )
                        },
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KairoPrimaryContainer,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Create",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Attachment Source Selector Dialog (Camera, Gallery, Files)
    if (showAttachmentSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentSourceDialog = false },
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Add Photo or File",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AttachmentOptionRow(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        onClick = {
                            attachmentName = "Photo from Camera"
                            showAttachmentSourceDialog = false
                        }
                    )
                    AttachmentOptionRow(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Gallery",
                        onClick = {
                            attachmentName = "Image from Gallery"
                            showAttachmentSourceDialog = false
                        }
                    )
                    AttachmentOptionRow(
                        icon = Icons.Default.Folder,
                        label = "Files",
                        onClick = {
                            attachmentName = "Document file"
                            showAttachmentSourceDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentSourceDialog = false }) {
                    Text("Cancel", color = Color(0xFFC8C4D9))
                }
            }
        )
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = KairoSurfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = KairoPrimary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
