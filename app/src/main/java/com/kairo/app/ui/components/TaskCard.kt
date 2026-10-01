package com.kairo.app.ui.components

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoErrorContainer
import com.kairo.app.ui.theme.KairoOnErrorContainer
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    onToggleCompletion: () -> Unit,
    onDeleteTask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val isOverdue = task.section == TaskSection.OVERDUE && !task.isCompleted
    val borderColor = if (isOverdue) {
        KairoError.copy(alpha = 0.35f)
    } else {
        KairoOutlineVariant.copy(alpha = 0.18f)
    }

    // Interactive bouncy checkbox animation
    val checkScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.05f else 0.82f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncyCheckboxScale"
    )

    val checkboxBg by animateColorAsState(
        targetValue = if (task.isCompleted) KairoPrimaryContainer else KairoSurfaceContainerHighest,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "checkboxBg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleCompletion() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = KairoSurfaceContainerLowest
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 1. Checkbox squircle with bounce animation on checking/unchecking
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
                    .scale(checkScale)
                    .background(checkboxBg, shape = RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (task.isCompleted) Color.Transparent else KairoOutlineVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleCompletion() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Middle Content Column: Task Name, Notes, and Chips
            Column(modifier = Modifier.weight(1f)) {
                // Task Name
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        Color.White
                    },
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                // Notes (if present, directly under the task name)
                val displayNotes = task.notes
                if (!displayNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = displayNotes,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF918EA2),
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Metadata Chips: [Priority] [Time] [Location] [Files]
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Priority Badge
                    val priorityBg = when (task.priority) {
                        Priority.LOW -> KairoSecondary.copy(alpha = 0.15f)
                        Priority.MEDIUM -> KairoPrimary.copy(alpha = 0.15f)
                        Priority.HIGH -> Color(0xFFFFB77D).copy(alpha = 0.18f)
                        Priority.URGENT -> KairoErrorContainer
                    }
                    val priorityText = when (task.priority) {
                        Priority.LOW -> KairoSecondary
                        Priority.MEDIUM -> KairoPrimary
                        Priority.HIGH -> Color(0xFFFFB77D)
                        Priority.URGENT -> KairoOnErrorContainer
                    }
                    TagBadge(
                        text = task.priority.label,
                        containerColor = priorityBg,
                        contentColor = priorityText
                    )

                    // Time / Due Date chip
                    val timeString = task.dueTime ?: task.dueDisplay
                    Row(
                        modifier = Modifier
                            .background(KairoSurfaceContainerHigh, RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val icon = if (isOverdue) Icons.Default.Warning else Icons.Default.Schedule
                        val tint = if (isOverdue) KairoError else KairoPrimary
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = timeString,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOverdue) KairoError else Color(0xFFC8C4D9)
                        )
                    }

                    // Location chip (if present)
                    val locationStr = task.location ?: if (task.contextType == com.kairo.app.data.model.ContextType.LOCATION) task.contextLabel else null
                    if (!locationStr.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .background(KairoSurfaceContainerHigh, RoundedCornerShape(100.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF918EA2),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = locationStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFC8C4D9)
                            )
                        }
                    }

                    // Attachment / Files chip (if present)
                    val fileStr = task.attachmentName ?: if (task.contextType == com.kairo.app.data.model.ContextType.ATTACHMENT) task.contextLabel else null
                    if (!fileStr.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .background(KairoSurfaceContainerHigh, RoundedCornerShape(100.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = Color(0xFF918EA2),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = fileStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFC8C4D9)
                            )
                        }
                    }
                }
            }

            // 3. Three Dots Action Menu (`...`) on far right
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color(0xFF918EA2),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(KairoSurfaceContainerHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("Details", color = Color.White) },
                        onClick = {
                            menuExpanded = false
                            Toast.makeText(context, "Details: ${task.title}", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = Color.White) },
                        onClick = {
                            menuExpanded = false
                            Toast.makeText(context, "Edit coming soon", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add subtask", color = Color.White) },
                        onClick = {
                            menuExpanded = false
                            Toast.makeText(context, "Add subtask coming soon", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Adjust parent", color = Color.White) },
                        onClick = {
                            menuExpanded = false
                            Toast.makeText(context, "Adjust parent coming soon", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = KairoError) },
                        onClick = {
                            menuExpanded = false
                            onDeleteTask()
                            Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TagBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(containerColor, shape = RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp
        )
    }
}
