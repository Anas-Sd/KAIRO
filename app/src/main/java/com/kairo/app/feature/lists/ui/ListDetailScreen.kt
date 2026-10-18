package com.kairo.app.feature.lists.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.feature.lists.model.ListIconCatalog
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow

@Composable
fun ListDetailScreen(
    listId: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val allListsWithStats by ListRepository.listsWithStats.collectAsState()
    val allItems by ListRepository.items.collectAsState()

    val currentListStats = allListsWithStats.find { it.list.id == listId }
    val currentList = currentListStats?.list

    if (currentList == null) {
        // List was deleted or not found
        onBack()
        return
    }

    val listItems = remember(allItems, listId) {
        allItems.filter { it.listId == listId }
    }

    val activeItems = remember(listItems) {
        listItems.filter { !it.isCompleted }
    }

    val completedItems = remember(listItems) {
        listItems.filter { it.isCompleted }
    }

    // Two sections: Active (expanded) and Completed (collapsed by default per user request)
    var isCompletedSectionExpanded by remember { mutableStateOf(false) }

    // Quick add inline
    var inlineInputText by remember { mutableStateOf("") }

    // State tracking for nested subtasks expansion
    val expandedSubtaskItemIds = remember { mutableStateListOf<String>() }

    // Menus & Dialog states
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditListDialog by remember { mutableStateOf(false) }
    var showDeleteListDialog by remember { mutableStateOf(false) }

    var selectedItemForDetails by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForEdit by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForAdjustParent by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForDelete by remember { mutableStateOf<ListItem?>(null) }
    var parentIdForNewSubtask by remember { mutableStateOf<String?>(null) }
    var showNewSubtaskDialog by remember { mutableStateOf(false) }

    fun commitInlineItem() {
        val clean = inlineInputText.trim()
        if (clean.isNotBlank()) {
            val lines = clean.split("\n", "\r").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size == 1) {
                ListRepository.addListItem(listId = listId, title = lines.first())
            } else {
                ListRepository.addMultipleListItems(listId = listId, titles = lines)
            }
            inlineInputText = ""
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = KairoSurfaceContainer,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(KairoPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ListIconCatalog.getIcon(currentList.iconName),
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = currentList.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // 3-dots Menu
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "List Options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(KairoSurfaceContainerHigh)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit List") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = KairoPrimary)
                                },
                                onClick = {
                                    menuExpanded = false
                                    showEditListDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Check All") },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981))
                                },
                                onClick = {
                                    menuExpanded = false
                                    ListRepository.checkAllItems(listId, true)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Uncheck All") },
                                leadingIcon = {
                                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                onClick = {
                                    menuExpanded = false
                                    ListRepository.checkAllItems(listId, false)
                                }
                            )

                            HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))

                            DropdownMenuItem(
                                text = { Text("Delete List", color = KairoHighUrgent) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = KairoHighUrgent)
                                },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteListDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = KairoBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Overall List Progress Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = KairoSurfaceContainer,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentListStats.completedCount} of ${currentListStats.totalCount} completed",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (currentListStats.progressPercentage == 100) Color(0xFF10B981).copy(alpha = 0.15f) else KairoPrimary.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                if (currentListStats.progressPercentage == 100) Color(0xFF10B981).copy(alpha = 0.4f) else KairoPrimary.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = "${currentListStats.progressPercentage}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentListStats.progressPercentage == 100) Color(0xFF10B981) else KairoPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val progressFloat = if (currentListStats.totalCount > 0) {
                        currentListStats.completedCount.toFloat() / currentListStats.totalCount.toFloat()
                    } else 0f

                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (currentListStats.progressPercentage == 100) Color(0xFF10B981) else KairoPrimary,
                        trackColor = KairoSurfaceContainerLow
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Add Item Row (Enter to add next)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KairoSurfaceContainer,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inlineInputText,
                        onValueChange = { inlineInputText = it },
                        placeholder = { Text("Add an item... (press Enter to add)", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { commitInlineItem() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = KairoPrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = { commitInlineItem() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KairoPrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Item",
                            tint = KairoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Scrollable Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. ACTIVE ITEMS SECTION
                if (activeItems.isEmpty() && completedItems.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KairoSurfaceContainerLow,
                            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "List is empty",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Type an item above to start your checklist",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Top-level active items (parentId == null)
                    val rootActiveItems = activeItems.filter { it.parentId == null }
                    items(rootActiveItems, key = { it.id }) { rootItem ->
                        ListItemCard(
                            item = rootItem,
                            allItemsInList = listItems,
                            expandedIds = expandedSubtaskItemIds,
                            onToggleSubtasksExpand = { id ->
                                if (expandedSubtaskItemIds.contains(id)) expandedSubtaskItemIds.remove(id)
                                else expandedSubtaskItemIds.add(id)
                            },
                            onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                            onItemClick = { selectedItemForDetails = it },
                            onAddSubtask = { parentId ->
                                parentIdForNewSubtask = parentId
                                showNewSubtaskDialog = true
                            },
                            onAdjustParent = { selectedItemForAdjustParent = it },
                            onEdit = { selectedItemForEdit = it },
                            onDelete = { selectedItemForDelete = it }
                        )
                    }

                    // 2. COMPLETED ITEMS SECTION (Collapsed by default!)
                    if (completedItems.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))

                            val chevronRotation by animateFloatAsState(
                                targetValue = if (isCompletedSectionExpanded) 180f else 0f,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "completed_chevron"
                            )

                            // Collapsible Section Header
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = KairoSurfaceContainer,
                                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.18f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isCompletedSectionExpanded = !isCompletedSectionExpanded }
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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Completed (${completedItems.size})",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(chevronRotation)
                                    )
                                }
                            }
                        }

                        if (isCompletedSectionExpanded) {
                            items(completedItems, key = { it.id }) { completedItem ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = KairoSurfaceContainerLow,
                                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { ListRepository.toggleItemCompleted(completedItem.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Uncheck item",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = completedItem.title,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textDecoration = TextDecoration.LineThrough,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = { selectedItemForDelete = completedItem },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOGS & BOTTOM SHEETS
    // ==========================================

    // Edit List Dialog
    if (showEditListDialog) {
        CreateListDialog(
            initialList = currentList,
            onDismiss = { showEditListDialog = false },
            onConfirm = { name, iconName, _ ->
                ListRepository.updateList(currentList.id, name, iconName)
                showEditListDialog = false
            }
        )
    }

    // Delete List Confirmation Dialog
    if (showDeleteListDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteListDialog = false },
            containerColor = KairoSurfaceContainer,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Delete '${currentList.name}'?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this list and all its items? This action cannot be undone.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteListDialog = false
                        ListRepository.deleteList(currentList.id)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KairoHighUrgent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteListDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
            }
        )
    }

    // Item Details Bottom Sheet
    if (selectedItemForDetails != null) {
        ListItemDetailsBottomSheet(
            item = selectedItemForDetails!!,
            allItemsInList = listItems,
            onDismiss = { selectedItemForDetails = null },
            onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
            onEditClicked = {
                val it = selectedItemForDetails!!
                selectedItemForDetails = null
                selectedItemForEdit = it
            },
            onAddSubtaskClicked = {
                val it = selectedItemForDetails!!
                selectedItemForDetails = null
                parentIdForNewSubtask = it.id
                showNewSubtaskDialog = true
            },
            onAdjustParentClicked = {
                val it = selectedItemForDetails!!
                selectedItemForDetails = null
                selectedItemForAdjustParent = it
            },
            onDeleteClicked = {
                val it = selectedItemForDetails!!
                selectedItemForDetails = null
                selectedItemForDelete = it
            }
        )
    }

    // Edit Item Dialog
    if (selectedItemForEdit != null) {
        EditListItemDialog(
            item = selectedItemForEdit!!,
            onDismiss = { selectedItemForEdit = null },
            onConfirm = { title, notes, targetDate, targetDateMillis ->
                ListRepository.updateListItem(
                    selectedItemForEdit!!.copy(
                        title = title,
                        notes = notes,
                        targetDate = targetDate,
                        targetDateMillis = targetDateMillis
                    )
                )
                selectedItemForEdit = null
            }
        )
    }

    // Adjust Parent Dialog
    if (selectedItemForAdjustParent != null) {
        AdjustParentListItemDialog(
            item = selectedItemForAdjustParent!!,
            allItemsInList = listItems,
            onDismiss = { selectedItemForAdjustParent = null },
            onMoveConfirmed = { newParentId, moveSubtasks ->
                ListRepository.adjustItemParent(selectedItemForAdjustParent!!.id, newParentId, moveSubtasks)
                selectedItemForAdjustParent = null
            }
        )
    }

    // Delete Item Confirm Dialog
    if (selectedItemForDelete != null) {
        val hasSubtasks = listItems.any { it.parentId == selectedItemForDelete!!.id }
        DeleteListItemConfirmDialog(
            item = selectedItemForDelete!!,
            hasSubtasks = hasSubtasks,
            onDismiss = { selectedItemForDelete = null },
            onConfirmDelete = { deleteSubtasks ->
                ListRepository.deleteListItem(selectedItemForDelete!!.id, deleteSubtasks)
                selectedItemForDelete = null
            }
        )
    }

    // Add Subtask Dialog
    if (showNewSubtaskDialog && parentIdForNewSubtask != null) {
        var subtaskTitle by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        val parentItem = listItems.find { it.id == parentIdForNewSubtask }

        AlertDialog(
            onDismissRequest = {
                showNewSubtaskDialog = false
                parentIdForNewSubtask = null
            },
            containerColor = KairoSurfaceContainer,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Add Subtask",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    if (parentItem != null) {
                        Text(
                            text = "Under: ${parentItem.title}",
                            fontSize = 12.sp,
                            color = KairoPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = subtaskTitle,
                        onValueChange = {
                            subtaskTitle = it
                            errorMsg = null
                        },
                        label = { Text("Subtask Name") },
                        placeholder = { Text("e.g. Call vendor, buy batteries...") },
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
                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = subtaskTitle.trim()
                        if (clean.isBlank()) {
                            errorMsg = "Please enter subtask name"
                            return@Button
                        }
                        ListRepository.addListItem(
                            listId = listId,
                            title = clean,
                            parentId = parentIdForNewSubtask
                        )
                        // Auto-expand parent so new subtask is immediately visible
                        if (!expandedSubtaskItemIds.contains(parentIdForNewSubtask)) {
                            expandedSubtaskItemIds.add(parentIdForNewSubtask!!)
                        }
                        showNewSubtaskDialog = false
                        parentIdForNewSubtask = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary, contentColor = Color(0xFF1E1F26))
                ) {
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showNewSubtaskDialog = false
                        parentIdForNewSubtask = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
            }
        )
    }
}

/**
 * Recursive Tree Card for List Items with Subtasks
 */
@Composable
private fun ListItemCard(
    item: ListItem,
    allItemsInList: List<ListItem>,
    expandedIds: List<String>,
    depth: Int = 0,
    onToggleSubtasksExpand: (id: String) -> Unit,
    onToggleCompleted: (id: String) -> Unit,
    onItemClick: (item: ListItem) -> Unit,
    onAddSubtask: (parentId: String) -> Unit,
    onAdjustParent: (item: ListItem) -> Unit,
    onEdit: (item: ListItem) -> Unit,
    onDelete: (item: ListItem) -> Unit
) {
    val childItems = remember(allItemsInList, item.id) {
        allItemsInList.filter { it.parentId == item.id && !it.isCompleted }
    }
    val hasChildren = childItems.isNotEmpty()
    val isExpanded = expandedIds.contains(item.id)

    var itemMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (depth == 0) KairoSurfaceContainer else KairoSurfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (depth == 0) KairoOutlineVariant.copy(alpha = 0.2f) else KairoOutlineVariant.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onItemClick(item) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Complete checkbox
                IconButton(
                    onClick = { onToggleCompleted(item.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle Complete",
                        tint = if (item.isCompleted) Color(0xFF10B981) else KairoOutlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Title & Target Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!item.targetDate.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.targetDate,
                                fontSize = 10.sp,
                                color = KairoPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Subtask expand toggle button if has children
                if (hasChildren) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        label = "subtask_expand"
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = KairoPrimary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { onToggleSubtasksExpand(item.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${childItems.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = KairoPrimary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Expand subtasks",
                                tint = KairoPrimary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(chevronRotation)
                            )
                        }
                    }
                }

                // Item 3-dots Menu
                Box {
                    IconButton(
                        onClick = { itemMenuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Item Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = itemMenuExpanded,
                        onDismissRequest = { itemMenuExpanded = false },
                        modifier = Modifier.background(KairoSurfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Subtask") },
                            leadingIcon = { Icon(Icons.Default.Add, null, tint = KairoPrimary) },
                            onClick = {
                                itemMenuExpanded = false
                                onAddSubtask(item.id)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Adjust Parent") },
                            leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = KairoPrimary) },
                            onClick = {
                                itemMenuExpanded = false
                                onAdjustParent(item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = KairoPrimary) },
                            onClick = {
                                itemMenuExpanded = false
                                onEdit(item)
                            }
                        )

                        HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))

                        DropdownMenuItem(
                            text = { Text("Delete", color = KairoHighUrgent) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = KairoHighUrgent) },
                            onClick = {
                                itemMenuExpanded = false
                                onDelete(item)
                            }
                        )
                    }
                }
            }
        }

        // Render subtasks recursively when expanded
        if (hasChildren && isExpanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                childItems.forEach { child ->
                    ListItemCard(
                        item = child,
                        allItemsInList = allItemsInList,
                        expandedIds = expandedIds,
                        depth = depth + 1,
                        onToggleSubtasksExpand = onToggleSubtasksExpand,
                        onToggleCompleted = onToggleCompleted,
                        onItemClick = onItemClick,
                        onAddSubtask = onAddSubtask,
                        onAdjustParent = onAdjustParent,
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}
