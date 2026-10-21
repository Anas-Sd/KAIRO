package com.kairo.app.feature.lists.ui

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
import androidx.compose.material.icons.filled.Close
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

@Composable
fun EditListItemDialog(
    item: ListItem,
    hasSubtasks: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (title: String, iconName: String, notes: String?, targetDate: String?, targetDateMillis: Long?) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var selectedIcon by remember { mutableStateOf(item.iconName) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var showCustomEmojiDialog by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }

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
                // Header: for parent show icon container + title; for leaf show title only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hasSubtasks) {
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
                        }
                        Text(
                            text = if (hasSubtasks) "Edit Parent Task" else "Edit Item",
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

                // Title Input - for leaf node, this is the ONLY editable field in the popup
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = null
                    },
                    label = { Text(if (hasSubtasks) "Parent Task Name" else "Item Name") },
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

                // Swiping Icons & Emoji Row ONLY for Parent Tasks (leaf nodes do not show icon picker)
                if (hasSubtasks) {
                    Spacer(modifier = Modifier.height(12.dp))

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
                                titleError = if (hasSubtasks) "Please enter a parent task name" else "Please enter an item name"
                                return@Button
                            }
                            onConfirm(
                                cleanTitle,
                                if (hasSubtasks) selectedIcon else item.iconName,
                                item.notes,
                                item.targetDate,
                                item.targetDateMillis
                            )
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

    // Custom Emoji Dialog (only used when editing parent task)
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
