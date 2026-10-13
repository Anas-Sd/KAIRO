package com.kairo.app.feature.habits.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitNoteDialog(
    habitTitle: String,
    habitId: String,
    state: HabitDayState,
    initialNote: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var noteText by remember { mutableStateOf(initialNote ?: "") }
    var isDropdownOpen by remember { mutableStateOf(false) }

    // Fetch all recent suggestions for this habit
    val recentNotes = remember(habitId) {
        HabitRepository.getRecentNotes(habitId)
    }

    val filteredSuggestions = remember(noteText, recentNotes) {
        if (noteText.isBlank()) {
            recentNotes
        } else {
            recentNotes.filter { it.contains(noteText, ignoreCase = true) && !it.equals(noteText, ignoreCase = true) }
        }
    }

    val isDone = state == HabitDayState.DONE
    val dialogTitle = if (isDone) "Add a Note (Optional)" else "Reason for Missing (Optional)"
    val dialogSubtitle = if (isDone) {
        "What did you accomplish for \"$habitTitle\"?"
    } else {
        "What held you back from \"$habitTitle\"?"
    }
    val accentColor = if (isDone) KairoSecondary else Color(0xFFFFB77D)

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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = dialogTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = dialogSubtitle,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Note Input Box with Dropdown Menu
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            isDropdownOpen = it.isNotBlank() && filteredSuggestions.isNotEmpty()
                        },
                        label = { Text(if (isDone) "Note" else "Reason", fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                text = if (isDone) "e.g. Read 25 pages, felt energized" else "e.g. Tired, busy with work, traveled",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        },
                        textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Inline Suggestions (No focus-stealing popup window)
                    if (isDropdownOpen && filteredSuggestions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KairoSurfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                filteredSuggestions.take(5).forEach { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                noteText = suggestion
                                                isDropdownOpen = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = suggestion,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent Suggestions Chips
                if (recentNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Recent Suggestions:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recentNotes.take(6).forEach { suggestion ->
                            val isCurrentSelected = noteText.equals(suggestion, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(
                                        if (isCurrentSelected) accentColor.copy(alpha = 0.25f)
                                        else KairoSurfaceContainerHighest
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrentSelected) accentColor else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(100.dp)
                                    )
                                    .clickable {
                                        noteText = suggestion
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrentSelected) accentColor else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Skip / Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onConfirm(null) }
                    ) {
                        Text(
                            text = "Skip",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                onConfirm(noteText.trim().takeIf { it.isNotBlank() })
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDone) KairoSecondary else KairoPrimaryContainer
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "Save Note",
                                color = if (isDone) Color(0xFF003822) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
