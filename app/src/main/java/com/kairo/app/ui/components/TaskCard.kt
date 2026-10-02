package com.kairo.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    onToggleCompletion: () -> Unit,
    onDeleteTask: () -> Unit = {},
    onShowDetails: () -> Unit = {},
    onEditTask: () -> Unit = {},
    onAddSubtask: () -> Unit = {},
    onAdjustParent: () -> Unit = {},
    hasSubtasks: Boolean = false,
    subtaskProgress: Pair<Int, Int>? = null,
    parentTitle: String? = null,
    onDrillDown: () -> Unit = {},
    onNavigateToParent: (parentId: String) -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    isBeingDragged: Boolean = false,
    dragOffsetY: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }

    // Local animated state for instant tactile response
    var isLocallyCompleted by remember(task.id, task.isCompleted) { mutableStateOf(task.isCompleted) }
    val bounceScale = remember { Animatable(1f) }

    val isOverdue = task.section == TaskSection.OVERDUE && !isLocallyCompleted
    val borderColor = if (isOverdue) {
        KairoError.copy(alpha = 0.35f)
    } else {
        KairoOutlineVariant.copy(alpha = 0.18f)
    }

    val checkboxBg by animateColorAsState(
        targetValue = if (isLocallyCompleted) KairoPrimaryContainer else KairoSurfaceContainerHighest,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "checkboxBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isLocallyCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f) else Color.White,
        animationSpec = tween(durationMillis = 200),
        label = "textColor"
    )

    fun handleToggle() {
        val nextCompleted = !isLocallyCompleted
        isLocallyCompleted = nextCompleted
        coroutineScope.launch {
            // Tactile squash, stretch, and spring bounce
            bounceScale.animateTo(
                targetValue = 1.35f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
            )
            bounceScale.animateTo(
                targetValue = 0.88f,
                animationSpec = tween(durationMillis = 80, easing = LinearOutSlowInEasing)
            )
            bounceScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
            )
            // Visual settling window before section rearrangement
            delay(280)
            onToggleCompletion()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isBeingDragged) {
                    Modifier
                        .zIndex(2f)
                        .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .then(
                if (hasSubtasks) {
                    Modifier.clickable { onDrillDown() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBeingDragged) KairoSurfaceContainerHigh else KairoSurfaceContainerLowest
        ),
        border = BorderStroke(1.dp, if (isBeingDragged) KairoPrimary else borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Drag Handle (for reordering tasks up/down)
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 8.dp)
                    .size(24.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = if (isBeingDragged) KairoPrimary else Color(0xFF918EA2),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(24.dp)
                        .scale(bounceScale.value)
                        .background(checkboxBg, shape = RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (isLocallyCompleted) Color.Transparent else KairoOutlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { handleToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLocallyCompleted) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (hasSubtasks) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = Icons.Default.SubdirectoryArrowRight,
                        contentDescription = "Has subtasks",
                        tint = KairoPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Middle Content Column: Task Name, Notes, and Equal-Height Capsules
            Column(modifier = Modifier.weight(1f)) {
                // Task Name
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor,
                    textDecoration = if (isLocallyCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                // Notes (directly under the task name if present)
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
                // All items share the EXACT SAME capsule height (24.dp) and pill shape
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Priority Capsule
                    val priorityBg = when (task.priority) {
                        Priority.LOW -> Color(0xFF16A34A) // Green bg
                        Priority.MEDIUM -> Color(0xFF2563EB) // Blue bg
                        Priority.HIGH -> Color(0xFFDC2626) // Red bg
                        Priority.URGENT -> Color(0xFFDC2626) // Same as Red
                    }
                    val priorityText = Color.White // White text for all priorities
                    TaskCapsule(
                        text = task.priority.label,
                        containerColor = priorityBg,
                        contentColor = priorityText
                    )

                    // Date & Time Capsules
                    val isUpcomingTask = task.section == TaskSection.UPCOMING ||
                        (!task.dueDate.equals("Today", ignoreCase = true) && task.dueDate.isNotBlank())

                    // Date Capsule (always visible for upcoming tasks or non-today tasks)
                    if (isUpcomingTask || isOverdue) {
                        TaskCapsule(
                            icon = if (isOverdue) Icons.Default.Warning else Icons.Default.CalendarToday,
                            iconTint = if (isOverdue) KairoError else KairoPrimary,
                            text = task.dueDate,
                            containerColor = KairoSurfaceContainerHigh,
                            contentColor = if (isOverdue) KairoError else Color(0xFFC8C4D9)
                        )
                    }

                    // Time Capsule (if due time is present, or if it is a Today task)
                    val displayTime = task.dueTime ?: if (!isUpcomingTask && !isOverdue) task.dueDate else null
                    if (!displayTime.isNullOrBlank()) {
                        TaskCapsule(
                            icon = Icons.Default.Schedule,
                            iconTint = KairoPrimary,
                            text = displayTime,
                            containerColor = KairoSurfaceContainerHigh,
                            contentColor = Color(0xFFC8C4D9)
                        )
                    }

                    // Location Capsule: Sky blue bg with just location icon (similar to attachment icon)
                    val locationStr = task.location
                    if (!locationStr.isNullOrBlank()) {
                        TaskCapsule(
                            icon = Icons.Default.Place,
                            iconTint = Color.White,
                            text = null, // Only location icon inside capsule
                            containerColor = Color(0xFF0284C7), // Sky blue bg
                            contentColor = Color.White
                        )
                    }

                    // Attachment Capsule: Yellow bg with attachment symbol
                    val fileStr = task.attachmentName
                    if (!fileStr.isNullOrBlank()) {
                        TaskCapsule(
                            icon = Icons.Default.AttachFile,
                            iconTint = Color(0xFF1A1A24),
                            text = null, // Only the attachment icon inside the capsule
                            containerColor = Color(0xFFEAB308), // Yellow bg
                            contentColor = Color(0xFF1A1A24)
                        )
                    }

                    // Subtask Progress Capsule (e.g. "3/10 done")
                    if (hasSubtasks && subtaskProgress != null && subtaskProgress.second > 0) {
                        TaskCapsule(
                            icon = Icons.Default.AccountTree,
                            iconTint = KairoPrimary,
                            text = "${subtaskProgress.first}/${subtaskProgress.second} done",
                            containerColor = KairoPrimary.copy(alpha = 0.12f),
                            contentColor = KairoPrimary
                        )
                    }

                    // Parent breadcrumb label (when shown in search / flat views) - Green bg with white text & clickable
                    if (!parentTitle.isNullOrBlank() && task.parentId != null) {
                        TaskCapsule(
                            icon = Icons.Default.Folder,
                            iconTint = Color.White,
                            text = "Under: $parentTitle",
                            containerColor = Color(0xFF16A34A), // Green bg
                            contentColor = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .clickable { onNavigateToParent(task.parentId) }
                        )
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
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = KairoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onShowDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = Color.White) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color(0xFFFFB77D),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEditTask()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add subtask", color = Color.White) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "Add subtask",
                                tint = Color(0xFFC8C4D9),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAddSubtask()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Adjust parent", color = Color.White) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = "Adjust parent",
                                tint = Color(0xFFC8C4D9),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAdjustParent()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = KairoError) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = KairoError,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteTask()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Standard uniform capsule badge ensuring Priority, Time, Location,
 * and Attachment chips all share the exact same height, padding, and pill geometry.
 */
@Composable
fun TaskCapsule(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    text: String? = null
) {
    Row(
        modifier = modifier
            .height(24.dp)
            .background(containerColor, RoundedCornerShape(100.dp))
            .padding(horizontal = if (text == null) 8.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 11.sp
            )
        }
    }
}
