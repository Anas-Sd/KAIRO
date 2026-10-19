package com.kairo.app.feature.lists.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kairo.app.feature.lists.model.ListIconCatalog
import com.kairo.app.feature.lists.model.ListIconDisplay
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerLow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun EditListItemDialog(
    item: ListItem,
    onDismiss: () -> Unit,
    onConfirm: (title: String, iconName: String, notes: String?, targetDate: String?, targetDateMillis: Long?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(item.title) }
    var selectedIcon by remember { mutableStateOf(item.iconName) }
    var notes by remember { mutableStateOf(item.notes ?: "") }
    var targetDate by remember { mutableStateOf(item.targetDate) }
    var targetDateMillis by remember { mutableStateOf(item.targetDateMillis) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var showCustomEmojiDialog by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        targetDateMillis?.let { cal.timeInMillis = it }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                targetDateMillis = chosen.timeInMillis
                targetDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(chosen.time)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
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
                // Header with current icon preview
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
                                .background(KairoPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ListIconDisplay(
                                iconKey = selectedIcon,
                                tint = KairoPrimary,
                                modifier = Modifier.size(20.dp),
                                emojiSize = 18.sp
                            )
                        }
                        Text(
                            text = "Edit Item",
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

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = null
                    },
                    label = { Text("Item Name") },
                    singleLine = true,
                    isError = titleError != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        cursorColor = KairoPrimary,
                        focusedLabelColor = KairoPrimary,
                        unfocusedContainerColor = KairoSurfaceContainerLow,
                        focusedContainerColor = KairoSurfaceContainerLow
                    )
                )

                if (titleError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = titleError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Swiping Icons & Emoji Row below the item name
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Emoji Input Button (+ Emoji)
                    item {
                        val isCustomActive = selectedIcon.startsWith("emoji:") &&
                                !ListIconCatalog.popularEmojis.contains(selectedIcon.removePrefix("emoji:"))

                        Surface(
                            shape = CircleShape,
                            color = if (isCustomActive) KairoPrimary else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isCustomActive) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable {
                                    customEmojiInput = if (isCustomActive) selectedIcon.removePrefix("emoji:") else ""
                                    showCustomEmojiDialog = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isCustomActive) {
                                    Text(
                                        text = selectedIcon.removePrefix("emoji:"),
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AddReaction,
                                        contentDescription = "Custom Emoji",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Popular Emojis
                    items(ListIconCatalog.popularEmojis) { emoji ->
                        val emojiKey = "emoji:$emoji"
                        val isSelected = selectedIcon == emojiKey || selectedIcon == emoji

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) KairoPrimary else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { selectedIcon = emojiKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = emoji,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Material Curated Icons
                    items(ListIconCatalog.icons) { iconOption ->
                        val isSelected = selectedIcon.equals(iconOption.key, ignoreCase = true)

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) KairoPrimary else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { selectedIcon = iconOption.key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconOption.icon,
                                    contentDescription = iconOption.label,
                                    tint = if (isSelected) Color(0xFF1E1F26) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notes Input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        cursorColor = KairoPrimary,
                        focusedLabelColor = KairoPrimary,
                        unfocusedContainerColor = KairoSurfaceContainerLow,
                        focusedContainerColor = KairoSurfaceContainerLow
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Optional Target Date
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openDatePicker() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (targetDate != null) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = targetDate ?: "Set Target Date (Optional)",
                                fontSize = 13.sp,
                                color = if (targetDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (targetDate != null) {
                            IconButton(
                                onClick = {
                                    targetDate = null
                                    targetDateMillis = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val cleanTitle = title.trim()
                            if (cleanTitle.isBlank()) {
                                titleError = "Please enter an item name"
                                return@Button
                            }
                            onConfirm(cleanTitle, selectedIcon, notes.trim().takeIf { it.isNotBlank() }, targetDate, targetDateMillis)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KairoPrimary,
                            contentColor = Color(0xFF1E1F26)
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Custom Emoji Dialog
    if (showCustomEmojiDialog) {
        AlertDialog(
            onDismissRequest = { showCustomEmojiDialog = false },
            containerColor = KairoSurfaceContainer,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Custom Emoji",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter any emoji to use as the icon:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customEmojiInput,
                        onValueChange = { customEmojiInput = it },
                        placeholder = { Text("e.g. 🍕, 🎯, ⚡") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KairoPrimary,
                            cursorColor = KairoPrimary,
                            unfocusedContainerColor = KairoSurfaceContainerLow,
                            focusedContainerColor = KairoSurfaceContainerLow
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customEmojiInput.trim()
                        if (trimmed.isNotBlank()) {
                            selectedIcon = "emoji:$trimmed"
                        }
                        showCustomEmojiDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary, contentColor = Color(0xFF1E1F26))
                ) {
                    Text("Use Emoji", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCustomEmojiDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
            }
        )
    }
}
