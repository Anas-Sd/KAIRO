package com.kairo.app.feature.lists.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kairo.app.feature.lists.model.ListIconCatalog
import com.kairo.app.feature.lists.model.ListIconDisplay
import com.kairo.app.feature.lists.model.TaskList
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateListDialog(
    initialList: TaskList? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconName: String, initialSubtasks: List<String>) -> Unit
) {
    val isEditMode = initialList != null
    var name by remember { mutableStateOf(initialList?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initialList?.iconName ?: "list") }
    var subtaskInput by remember { mutableStateOf("") }
    val subtasks = remember { mutableStateListOf<String>() }

    var nameError by remember { mutableStateOf<String?>(null) }
    var showCustomEmojiDialog by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }

    fun commitSubtask() {
        val trimmed = subtaskInput.trim()
        if (trimmed.isNotBlank()) {
            // Support multi-line paste: split by newlines
            val lines = trimmed.split("\n", "\r").map { it.trim() }.filter { it.isNotBlank() }
            subtasks.addAll(lines)
            subtaskInput = ""
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
                            text = if (isEditMode) "Edit List" else "New List",
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

                // List Name Input (Required)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("List Name (Required)") },
                    placeholder = { Text("e.g. Groceries, Sprint Tasks, Packing...") },
                    singleLine = true,
                    isError = nameError != null,
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

                if (nameError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nameError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Swiping Icons & Emoji Row below the list name
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

                if (!isEditMode) {
                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.18f))

                    Spacer(modifier = Modifier.height(12.dp))

                    // Multi-subtask quick add section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Tasks / Items",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (subtasks.isNotEmpty()) {
                            Text(
                                text = "${subtasks.size} added",
                                fontSize = 11.sp,
                                color = KairoPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = subtaskInput,
                        onValueChange = { subtaskInput = it },
                        placeholder = { Text("Type item & press Enter", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { commitSubtask() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KairoPrimary,
                            cursorColor = KairoPrimary,
                            unfocusedContainerColor = KairoSurfaceContainerLow,
                            focusedContainerColor = KairoSurfaceContainerLow
                        )
                    )

                    // 2. Added items rendered as capsules side by side (FlowRow), not swiping, see all at once
                    if (subtasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subtasks.forEachIndexed { index, itemText ->
                                Surface(
                                    shape = CircleShape,
                                    color = KairoSurfaceContainerLow,
                                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = itemText,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(KairoOutlineVariant.copy(alpha = 0.2f))
                                                .clickable { subtasks.removeAt(index) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove item",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel & Create / Save
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
                            val cleanName = name.trim()
                            if (cleanName.isBlank()) {
                                nameError = "Please enter a list name"
                                return@Button
                            }
                            commitSubtask()
                            onConfirm(cleanName, selectedIcon, subtasks.toList())
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
                        Text(
                            text = if (isEditMode) "Save" else "Create List",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Dialog for entering a custom emoji
    if (showCustomEmojiDialog) {
        AlertDialog(
            onDismissRequest = { showCustomEmojiDialog = false },
            title = {
                Text(
                    text = "Custom Emoji",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Type or paste any emoji from your keyboard to use as the list icon:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customEmojiInput,
                        onValueChange = { customEmojiInput = it },
                        placeholder = { Text("e.g. 🎯, 🚀, 💡, 🔥") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary, contentColor = Color(0xFF1E1F26)),
                    shape = RoundedCornerShape(10.dp)
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
