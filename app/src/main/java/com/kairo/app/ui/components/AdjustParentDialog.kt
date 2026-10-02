package com.kairo.app.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairo.app.data.model.Task
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest

private val TealActive = Color(0xFF26E0BA)

@Composable
fun AdjustParentDialog(
    task: Task,
    allTasks: List<Task>,
    onDismiss: () -> Unit,
    onMoveConfirmed: (newParentId: String?, moveSubtasks: Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // Selected parent ID: null represents "No parent (make top-level)"
    // Use an explicit sentinel wrapper to distinguish between unselected vs root selected
    var selectedParentId by remember { mutableStateOf<String?>(task.parentId) }
    var hasPickedTarget by remember { mutableStateOf(false) }
    var targetParentId by remember { mutableStateOf<String?>(null) }

    // Dialog state for "Move with its subtasks" vs "Move only this task"
    var showMoveOptionsModal by remember { mutableStateOf(false) }

    // Direct subtasks of the task being edited
    val hasDirectSubtasks = remember(task, allTasks) {
        allTasks.any { it.parentId == task.id }
    }

    // Loop forbidden IDs: self and all recursive descendants (strictly hidden everywhere to prevent loops)
    val loopForbiddenIds = remember(task, allTasks) {
        val descendants = TaskRepository.getDescendantIds(task.id, allTasks)
        val set = mutableSetOf(task.id)
        set.addAll(descendants)
        set
    }

    val currentParentId = task.parentId

    // Expanded task IDs in the hierarchical tree view (auto-expand current parent and ancestors so siblings are visible)
    val expandedIds = remember(task, allTasks) {
        val list = mutableStateListOf<String>()
        task.parentId?.let { pId ->
            list.add(pId)
            list.addAll(TaskRepository.getAncestorIds(pId, allTasks))
        }
        list
    }

    fun handlePick(newParentId: String?) {
        targetParentId = newParentId
        if (hasDirectSubtasks) {
            showMoveOptionsModal = true
        } else {
            onMoveConfirmed(newParentId, false)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = KairoSurfaceContainerLowest,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Adjust parent",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Moving: ${task.title}",
                            fontSize = 12.sp,
                            color = Color(0xFF918EA2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFC8C4D9),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search tasks by name...",
                            fontSize = 13.sp,
                            color = Color(0xFF6B687C)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF918EA2),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF918EA2),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f),
                        unfocusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Task List Picker
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (searchQuery.isBlank()) {
                        // 1. "No parent (make top-level)" option at top
                        // Only show if the task is NOT already top-level
                        if (task.parentId != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (hasPickedTarget && selectedParentId == null) TealActive.copy(alpha = 0.12f)
                                    else KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedParentId = null
                                            hasPickedTarget = true
                                        }
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
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = null,
                                                tint = KairoPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "No parent (make top-level)",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Task will appear as a main root task",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF918EA2)
                                                )
                                            }
                                        }

                                        RadioButton(
                                            selected = (hasPickedTarget && selectedParentId == null),
                                            onClick = {
                                                selectedParentId = null
                                                hasPickedTarget = true
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = TealActive,
                                                unselectedColor = Color(0xFF6B687C)
                                            )
                                        )
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        // 2. Hierarchical Tree of eligible tasks
                        val topLevelTasks = allTasks.filter { it.parentId == null && !loopForbiddenIds.contains(it.id) }
                            .sortedBy { it.position }

                        if (topLevelTasks.isEmpty() && task.parentId == null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No other eligible parent tasks available",
                                        color = Color(0xFF6B687C),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            fun renderTree(parentTask: Task, depth: Int) {
                                val isCurrentParent = (parentTask.id == currentParentId)
                                val hasChildren = allTasks.any { it.parentId == parentTask.id && !loopForbiddenIds.contains(it.id) }
                                val isExpanded = expandedIds.contains(parentTask.id)
                                val isSelected = (hasPickedTarget && selectedParentId == parentTask.id)
                                val indentDp = (depth * 16).coerceAtMost(64).dp

                                item(key = parentTask.id) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) TealActive.copy(alpha = 0.12f) else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = indentDp)
                                            .clickable(enabled = !isCurrentParent) {
                                                selectedParentId = parentTask.id
                                                hasPickedTarget = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (hasChildren) {
                                                    IconButton(
                                                        onClick = {
                                                            if (isExpanded) expandedIds.remove(parentTask.id)
                                                            else expandedIds.add(parentTask.id)
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                            tint = Color(0xFFC8C4D9),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.size(24.dp))
                                                }

                                                Text(
                                                    text = parentTask.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isCurrentParent) Color(0xFF918EA2) else Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (isCurrentParent) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = KairoSurfaceContainerHighest.copy(alpha = 0.6f)
                                                    ) {
                                                        Text(
                                                            text = "Current parent",
                                                            fontSize = 10.sp,
                                                            color = Color(0xFFC8C4D9),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (!isCurrentParent) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedParentId = parentTask.id
                                                        hasPickedTarget = true
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = TealActive,
                                                        unselectedColor = Color(0xFF6B687C)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                if (hasChildren && isExpanded) {
                                    val children = allTasks.filter { it.parentId == parentTask.id && !loopForbiddenIds.contains(it.id) }
                                        .sortedBy { it.position }
                                    children.forEach { child ->
                                        renderTree(child, depth + 1)
                                    }
                                }
                            }

                            topLevelTasks.forEach { root ->
                                renderTree(root, 0)
                            }
                        }
                    } else {
                        // 3. Search Results: Flat list with full ancestor paths
                        val matchingTasks = allTasks.filter {
                            !loopForbiddenIds.contains(it.id) && it.id != currentParentId && it.title.contains(searchQuery, ignoreCase = true)
                        }

                        if (matchingTasks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching parent tasks found",
                                        color = Color(0xFF6B687C),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            items(matchingTasks, key = { it.id }) { itemTask ->
                                val isSelected = (hasPickedTarget && selectedParentId == itemTask.id)
                                val fullPath = TaskRepository.getTaskPath(itemTask.id, allTasks)

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) TealActive.copy(alpha = 0.12f) else KairoSurfaceContainerHighest.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedParentId = itemTask.id
                                            hasPickedTarget = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = itemTask.title,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = fullPath,
                                                fontSize = 11.sp,
                                                color = KairoPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedParentId = itemTask.id
                                                hasPickedTarget = true
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = TealActive,
                                                unselectedColor = Color(0xFF6B687C)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text("Cancel", color = Color(0xFFC8C4D9))
                    }

                    Button(
                        onClick = {
                            if (hasPickedTarget) {
                                handlePick(selectedParentId)
                            }
                        },
                        enabled = hasPickedTarget && selectedParentId != currentParentId,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary)
                    ) {
                        Text("Move", color = Color(0xFF130067), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Move Decision Modal (when task has subtasks)
    if (showMoveOptionsModal) {
        AlertDialog(
            onDismissRequest = { showMoveOptionsModal = false },
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Move Subtasks?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "'${task.title}' has subtasks. How would you like to move it?",
                    fontSize = 14.sp,
                    color = Color(0xFFC8C4D9)
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showMoveOptionsModal = false
                            onMoveConfirmed(targetParentId, true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Move with its subtasks", color = Color(0xFF130067), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showMoveOptionsModal = false
                            onMoveConfirmed(targetParentId, false)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Move only this task", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveOptionsModal = false }) {
                    Text("Cancel", color = Color(0xFF918EA2))
                }
            }
        )
    }
}
