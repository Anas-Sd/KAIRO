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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListItemDetailsBottomSheet(
    item: ListItem,
    allItemsInList: List<ListItem>,
    onDismiss: () -> Unit,
    onToggleCompleted: (itemId: String) -> Unit,
    onEditClicked: () -> Unit,
    onAddSubtaskClicked: () -> Unit,
    onAdjustParentClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onSelectSubtask: ((ListItem) -> Unit)? = null,
    onSubtaskAddSubtask: ((ListItem) -> Unit)? = null,
    onSubtaskAdjustParent: ((ListItem) -> Unit)? = null,
    onSubtaskEdit: ((ListItem) -> Unit)? = null,
    onSubtaskDelete: ((ListItem) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parentItem = allItemsInList.find { it.id == item.parentId }
    val directSubtasks = allItemsInList.filter { it.parentId == item.id }

    val dateFormat = SimpleDateFormat("h:mm a, MMM d, yyyy", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KairoSurfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(KairoOutlineVariant.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Row: Status Chip, Title, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (item.isCompleted) Color(0xFF10B981).copy(alpha = 0.15f) else KairoPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (item.isCompleted) Color(0xFF10B981).copy(alpha = 0.4f) else KairoPrimary.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (item.isCompleted) "Completed" else "Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCompleted) Color(0xFF10B981) else KairoPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title & Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onToggleCompleted(item.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (item.isCompleted) "Mark active" else "Mark complete",
                        tint = if (item.isCompleted) Color(0xFF10B981) else KairoOutlineVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )
            }

            // Parent link if any
            if (parentItem != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Sub-item of: ${parentItem.title}",
                            fontSize = 11.sp,
                            color = KairoPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Notes if any
            if (!item.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Notes",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.notes,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Target Date if any
            if (!item.targetDate.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Target Date: ${item.targetDate}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Subtasks preview if any
            if (directSubtasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sub-items (${directSubtasks.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    directSubtasks.forEach { sub ->
                        SubtaskItemRow(
                            sub = sub,
                            onToggleCompleted = onToggleCompleted,
                            onSubtaskClick = onSelectSubtask,
                            onAddSubtask = onSubtaskAddSubtask,
                            onAdjustParent = onSubtaskAdjustParent,
                            onEdit = onSubtaskEdit,
                            onDelete = onSubtaskDelete
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timestamps
            Text(
                text = "Created: ${dateFormat.format(Date(item.createdAt))}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (item.completedAt != null) {
                Text(
                    text = "Completed: ${dateFormat.format(Date(item.completedAt))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.18f))
            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Add Subtask, Adjust Parent, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Subtask
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAddSubtaskClicked() }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Subtask",
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Add Subtask", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Adjust Parent
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAdjustParentClicked() }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "Adjust Parent",
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Adjust Parent", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Edit
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditClicked() }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Edit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Delete
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KairoHighUrgent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, KairoHighUrgent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDeleteClicked() }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = KairoHighUrgent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Delete", fontSize = 10.sp, color = KairoHighUrgent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SubtaskItemRow(
    sub: ListItem,
    onToggleCompleted: (String) -> Unit,
    onSubtaskClick: ((ListItem) -> Unit)?,
    onAddSubtask: ((ListItem) -> Unit)?,
    onAdjustParent: ((ListItem) -> Unit)?,
    onEdit: ((ListItem) -> Unit)?,
    onDelete: ((ListItem) -> Unit)?
) {
    var subMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = KairoSurfaceContainerLow,
        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = onSubtaskClick != null) { onSubtaskClick?.invoke(sub) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onToggleCompleted(sub.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (sub.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (sub.isCompleted) Color(0xFF10B981) else KairoOutlineVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sub.title,
                fontSize = 12.sp,
                color = if (sub.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 3-dots Menu for this subtask
            Box {
                IconButton(
                    onClick = { subMenuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Subtask Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }

                DropdownMenu(
                    expanded = subMenuExpanded,
                    onDismissRequest = { subMenuExpanded = false },
                    modifier = Modifier.background(KairoSurfaceContainerHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Subtask") },
                        leadingIcon = { Icon(Icons.Default.Add, null, tint = KairoPrimary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            subMenuExpanded = false
                            onAddSubtask?.invoke(sub)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Adjust Parent") },
                        leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = KairoPrimary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            subMenuExpanded = false
                            onAdjustParent?.invoke(sub)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = KairoPrimary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            subMenuExpanded = false
                            onEdit?.invoke(sub)
                        }
                    )

                    HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))

                    DropdownMenuItem(
                        text = { Text("Delete", color = KairoHighUrgent) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = KairoHighUrgent, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            subMenuExpanded = false
                            onDelete?.invoke(sub)
                        }
                    )
                }
            }
        }
    }
}
