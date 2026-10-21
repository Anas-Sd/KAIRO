package com.kairo.app.feature.lists.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.LaunchedEffect
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
import com.kairo.app.feature.lists.model.ListIconDisplay
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.feature.lists.ui.AddMultipleItemsDialog
import com.kairo.app.feature.lists.ui.AdjustParentListDialog
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow

@Composable
fun ListDetailScreen(
    listId: String,
    onBack: () -> Unit
) {
    val allListsWithStats by ListRepository.listsWithStats.collectAsState()
    val allItems by ListRepository.items.collectAsState()

    val currentListStats = allListsWithStats.find { it.list.id == listId }
    val currentList = currentListStats?.list

    if (currentList == null) {
        onBack()
        return
    }

    val listItems = remember(allItems, listId) {
        allItems.filter { it.listId == listId }
    }

    // Breadcrumb navigation stack for infinite hierarchy drill-down
    val breadcrumbStack = remember { mutableStateListOf<ListItem>() }

    // Keep breadcrumb stack synchronized with updated items state
    LaunchedEffect(listItems) {
        val validIds = listItems.map { it.id }.toSet()
        val iter = breadcrumbStack.listIterator()
        while (iter.hasNext()) {
            if (!validIds.contains(iter.next().id)) {
                iter.remove()
            }
        }
    }

    val currentParent = remember(breadcrumbStack.toList(), listItems) {
        breadcrumbStack.lastOrNull()?.let { last -> listItems.find { it.id == last.id } }
    }

    val activeItems = remember(listItems) {
        listItems.filter { !it.isCompleted }
    }

    val completedItems = remember(listItems) {
        listItems.filter { it.isCompleted }
    }

    // Items scoped to current level (root if breadcrumbs empty, or children of currentParent)
    // Parent items (items that have subtasks) automatically float to the top above leaf items
    val scopedActiveItems = remember(activeItems, listItems, currentParent) {
        val base = if (currentParent == null) {
            activeItems.filter { it.parentId == null }
        } else {
            activeItems.filter { it.parentId == currentParent.id }
        }
        val (parents, leaves) = base.partition { item ->
            listItems.any { it.parentId == item.id }
        }
        parents.sortedBy { it.position } + leaves.sortedBy { it.position }
    }

    val scopedCompletedItems = remember(completedItems, listItems, currentParent) {
        val base = if (currentParent == null) {
            completedItems.filter { it.parentId == null }
        } else {
            completedItems.filter { it.parentId == currentParent.id }
        }
        val (parents, leaves) = base.partition { item ->
            listItems.any { it.parentId == item.id }
        }
        parents.sortedBy { it.position } + leaves.sortedBy { it.position }
    }

    // Two sections: Active (expanded) and Completed (collapsed by default)
    var isCompletedSectionExpanded by remember { mutableStateOf(false) }

    // Dialog states
    var showEditListDialog by remember { mutableStateOf(false) }
    var showAdjustParentListDialog by remember { mutableStateOf(false) }
    var showDeleteListDialog by remember { mutableStateOf(false) }

    var selectedItemForDetails by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForEdit by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForAdjustParent by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForDelete by remember { mutableStateOf<ListItem?>(null) }
    var targetParentForMultiAdd by remember { mutableStateOf<ListItem?>(null) }
    var showMultiAddDialog by remember { mutableStateOf(false) }

    // Back handling: If inside breadcrumbs, pop one level; else exit list
    BackHandler(enabled = breadcrumbStack.isNotEmpty()) {
        breadcrumbStack.removeLast()
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
                    IconButton(onClick = {
                        if (breadcrumbStack.isNotEmpty()) {
                            breadcrumbStack.removeLast()
                        } else {
                            onBack()
                        }
                    }) {
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
                        ListIconDisplay(
                            iconKey = currentList.iconName,
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp),
                            emojiSize = 18.sp
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

                    // Plus (+) button to add multiple items at once
                    IconButton(onClick = {
                        targetParentForMultiAdd = currentParent
                        showMultiAddDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Items",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Breadcrumbs Navigation Bar (shown when drilled into subtasks)
            if (breadcrumbStack.isNotEmpty()) {
                ListBreadcrumbBar(
                    listName = currentList.name,
                    listIcon = currentList.iconName,
                    breadcrumbStack = breadcrumbStack,
                    onNavigateBack = { breadcrumbStack.removeLast() },
                    onNavigateToCrumb = { index ->
                        if (index < 0) {
                            breadcrumbStack.clear()
                        } else {
                            val targetCount = index + 1
                            while (breadcrumbStack.size > targetCount) {
                                breadcrumbStack.removeLast()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. Parent Header Card (shown when drilled into a parent task)
            if (currentParent != null) {
                var parentCardMenuExpanded by remember { mutableStateOf(false) }
                val directChildren = listItems.filter { it.parentId == currentParent.id }
                val completedChildren = directChildren.count { it.isCompleted }
                val totalChildren = directChildren.size
                val percentage = if (totalChildren > 0) (completedChildren * 100) / totalChildren else 0
                val progressFloat = if (totalChildren > 0) completedChildren.toFloat() / totalChildren.toFloat() else 0f

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = KairoSurfaceContainer,
                    border = BorderStroke(1.2.dp, KairoPrimary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedItemForDetails = currentParent }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox to complete parent & all its subtasks
                            IconButton(
                                onClick = { ListRepository.toggleItemCompleted(currentParent.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentParent.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Toggle Complete",
                                    tint = if (currentParent.isCompleted) Color(0xFF10B981) else KairoOutlineVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // 42.dp Icon container Box
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KairoPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ListIconDisplay(
                                    iconKey = currentParent.iconName,
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(22.dp),
                                    emojiSize = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentParent.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (currentParent.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$completedChildren of $totalChildren completed",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Progress percentage pill
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (percentage == 100) Color(0xFF10B981).copy(alpha = 0.15f) else KairoPrimary.copy(alpha = 0.12f),
                                border = BorderStroke(
                                    1.dp,
                                    if (percentage == 100) Color(0xFF10B981).copy(alpha = 0.4f) else KairoPrimary.copy(alpha = 0.35f)
                                )
                            ) {
                                Text(
                                    text = "$percentage%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (percentage == 100) Color(0xFF10B981) else KairoPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // 3-dots Menu for Current Parent
                            Box {
                                IconButton(
                                    onClick = { parentCardMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Parent Actions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = parentCardMenuExpanded,
                                    onDismissRequest = { parentCardMenuExpanded = false },
                                    modifier = Modifier.background(KairoSurfaceContainerHigh)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add Subtask") },
                                        leadingIcon = { Icon(Icons.Default.Add, null, tint = KairoPrimary) },
                                        onClick = {
                                            parentCardMenuExpanded = false
                                            targetParentForMultiAdd = currentParent
                                            showMultiAddDialog = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Adjust Parent") },
                                        leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = KairoPrimary) },
                                        onClick = {
                                            parentCardMenuExpanded = false
                                            selectedItemForAdjustParent = currentParent
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Details") },
                                        leadingIcon = { Icon(Icons.Default.Info, null, tint = KairoPrimary) },
                                        onClick = {
                                            parentCardMenuExpanded = false
                                            selectedItemForDetails = currentParent
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = KairoPrimary) },
                                        onClick = {
                                            parentCardMenuExpanded = false
                                            selectedItemForEdit = currentParent
                                        }
                                    )

                                    HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))

                                    DropdownMenuItem(
                                        text = { Text("Delete", color = KairoHighUrgent) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = KairoHighUrgent) },
                                        onClick = {
                                            parentCardMenuExpanded = false
                                            selectedItemForDelete = currentParent
                                        }
                                    )
                                }
                            }
                        }

                        if (!currentParent.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentParent.notes,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 6.dp linear progress indicator matching Pic 1
                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (percentage == 100) Color(0xFF10B981) else KairoPrimary,
                            trackColor = KairoSurfaceContainerLow
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                // Overall List Progress Card at Root
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Main Scrollable Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active items
                if (scopedActiveItems.isEmpty() && scopedCompletedItems.isEmpty()) {
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
                                    text = if (currentParent != null) "No subtasks yet" else "List is empty",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (currentParent != null) "Click + at the top to add subtasks" else "Click + at the top to add items",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(scopedActiveItems, key = { _, item -> item.id }) { index, item ->
                        val childSubtasks = listItems.filter { it.parentId == item.id }
                        val hasSubtasks = childSubtasks.isNotEmpty()
                        val completedSubtasks = childSubtasks.count { it.isCompleted }

                        val canMoveUp = index > 0
                        val canMoveDown = index < scopedActiveItems.size - 1

                        if (hasSubtasks) {
                            ParentListItemCard(
                                item = item,
                                completedSubtasks = completedSubtasks,
                                totalSubtasks = childSubtasks.size,
                                canMoveUp = canMoveUp,
                                canMoveDown = canMoveDown,
                                onMoveUp = { ListRepository.moveItemUp(item.id) },
                                onMoveDown = { ListRepository.moveItemDown(item.id) },
                                onMoveToTop = { ListRepository.moveItemToTop(item.id) },
                                onMoveToBottom = { ListRepository.moveItemToBottom(item.id) },
                                onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                onDrillDown = { breadcrumbStack.add(item) },
                                onShowDetails = { selectedItemForDetails = item },
                                onAddSubtask = { parentId ->
                                    val target = listItems.find { it.id == parentId } ?: item
                                    targetParentForMultiAdd = target
                                    showMultiAddDialog = true
                                },
                                onAdjustParent = { selectedItemForAdjustParent = it },
                                onEdit = { selectedItemForEdit = it },
                                onDelete = { selectedItemForDelete = it }
                            )
                        } else {
                            LeafListItemCard(
                                item = item,
                                canMoveUp = canMoveUp,
                                canMoveDown = canMoveDown,
                                onMoveUp = { ListRepository.moveItemUp(item.id) },
                                onMoveDown = { ListRepository.moveItemDown(item.id) },
                                onMoveToTop = { ListRepository.moveItemToTop(item.id) },
                                onMoveToBottom = { ListRepository.moveItemToBottom(item.id) },
                                onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                onShowDetails = { selectedItemForDetails = item },
                                onAddSubtask = { parentId ->
                                    val target = listItems.find { it.id == parentId } ?: item
                                    targetParentForMultiAdd = target
                                    showMultiAddDialog = true
                                },
                                onAdjustParent = { selectedItemForAdjustParent = it },
                                onEdit = { selectedItemForEdit = it },
                                onDelete = { selectedItemForDelete = it }
                            )
                        }
                    }

                    // 5. COMPLETED ITEMS SECTION (Collapsed by default)
                    if (scopedCompletedItems.isNotEmpty()) {
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
                                            text = "Completed (${scopedCompletedItems.size})",
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
                            itemsIndexed(scopedCompletedItems, key = { _, completedItem -> completedItem.id }) { index, completedItem ->
                                val childSubtasks = listItems.filter { it.parentId == completedItem.id }
                                val hasSubtasks = childSubtasks.isNotEmpty()
                                val completedCount = childSubtasks.count { it.isCompleted }

                                val canMoveUp = index > 0
                                val canMoveDown = index < scopedCompletedItems.size - 1

                                if (hasSubtasks) {
                                    ParentListItemCard(
                                        item = completedItem,
                                        completedSubtasks = completedCount,
                                        totalSubtasks = childSubtasks.size,
                                        canMoveUp = canMoveUp,
                                        canMoveDown = canMoveDown,
                                        onMoveUp = { ListRepository.moveItemUp(completedItem.id) },
                                        onMoveDown = { ListRepository.moveItemDown(completedItem.id) },
                                        onMoveToTop = { ListRepository.moveItemToTop(completedItem.id) },
                                        onMoveToBottom = { ListRepository.moveItemToBottom(completedItem.id) },
                                        onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                        onDrillDown = { breadcrumbStack.add(completedItem) },
                                        onShowDetails = { selectedItemForDetails = completedItem },
                                        onAddSubtask = { parentId ->
                                            val target = listItems.find { it.id == parentId } ?: completedItem
                                            targetParentForMultiAdd = target
                                            showMultiAddDialog = true
                                        },
                                        onAdjustParent = { selectedItemForAdjustParent = it },
                                        onEdit = { selectedItemForEdit = it },
                                        onDelete = { selectedItemForDelete = it }
                                    )
                                } else {
                                    LeafListItemCard(
                                        item = completedItem,
                                        canMoveUp = canMoveUp,
                                        canMoveDown = canMoveDown,
                                        onMoveUp = { ListRepository.moveItemUp(completedItem.id) },
                                        onMoveDown = { ListRepository.moveItemDown(completedItem.id) },
                                        onMoveToTop = { ListRepository.moveItemToTop(completedItem.id) },
                                        onMoveToBottom = { ListRepository.moveItemToBottom(completedItem.id) },
                                        onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                        onShowDetails = { selectedItemForDetails = completedItem },
                                        onAddSubtask = { parentId ->
                                            val target = listItems.find { it.id == parentId } ?: completedItem
                                            targetParentForMultiAdd = target
                                            showMultiAddDialog = true
                                        },
                                        onAdjustParent = { selectedItemForAdjustParent = it },
                                        onEdit = { selectedItemForEdit = it },
                                        onDelete = { selectedItemForDelete = it }
                                    )
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
                        ListRepository.deleteList(currentList.id)
                        showDeleteListDialog = false
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
            onToggleCompleted = { itemId -> ListRepository.toggleItemCompleted(itemId) },
            onEditClicked = {
                selectedItemForEdit = selectedItemForDetails
                selectedItemForDetails = null
            },
            onAddSubtaskClicked = {
                targetParentForMultiAdd = selectedItemForDetails
                showMultiAddDialog = true
                selectedItemForDetails = null
            },
            onAdjustParentClicked = {
                selectedItemForAdjustParent = selectedItemForDetails
                selectedItemForDetails = null
            },
            onDeleteClicked = {
                selectedItemForDelete = selectedItemForDetails
                selectedItemForDetails = null
            },
            onSelectSubtask = { sub ->
                selectedItemForDetails = sub
            },
            onSubtaskAddSubtask = { sub ->
                targetParentForMultiAdd = sub
                showMultiAddDialog = true
                selectedItemForDetails = null
            },
            onSubtaskAdjustParent = { sub ->
                selectedItemForAdjustParent = sub
                selectedItemForDetails = null
            },
            onSubtaskEdit = { sub ->
                selectedItemForEdit = sub
                selectedItemForDetails = null
            },
            onSubtaskDelete = { sub ->
                selectedItemForDelete = sub
                selectedItemForDetails = null
            }
        )
    }

    // Edit Item Dialog
    if (selectedItemForEdit != null) {
        val itemToEdit = selectedItemForEdit!!
        EditListItemDialog(
            item = itemToEdit,
            onDismiss = { selectedItemForEdit = null },
            onConfirm = { updatedTitle, updatedIconName, updatedNotes, updatedTargetDate, updatedTargetDateMillis ->
                ListRepository.updateListItem(
                    itemToEdit.copy(
                        title = updatedTitle,
                        iconName = updatedIconName,
                        notes = updatedNotes,
                        targetDate = updatedTargetDate,
                        targetDateMillis = updatedTargetDateMillis
                    )
                )
                selectedItemForEdit = null
            }
        )
    }

    // Adjust Parent Dialog (Works for BOTH root items and subtasks!)
    if (selectedItemForAdjustParent != null) {
        AdjustParentListItemDialog(
            item = selectedItemForAdjustParent!!,
            allItemsInList = listItems,
            onDismiss = { selectedItemForAdjustParent = null },
            onMoveConfirmed = { newParentId, moveSubtasks ->
                ListRepository.adjustItemParent(selectedItemForAdjustParent!!.id, newParentId, moveSubtasks)
                selectedItemForAdjustParent = null
            },
            onConvertToStandalone = {
                val target = selectedItemForAdjustParent!!
                val wasInBreadcrumbs = breadcrumbStack.any { it.id == target.id }
                ListRepository.convertItemToStandaloneList(target.id)
                if (wasInBreadcrumbs) {
                    val idx = breadcrumbStack.indexOfFirst { it.id == target.id }
                    if (idx != -1) {
                        while (breadcrumbStack.size > idx) {
                            breadcrumbStack.removeLast()
                        }
                    }
                }
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
                val deletedId = selectedItemForDelete!!.id
                ListRepository.deleteListItem(deletedId, deleteSubtasks)
                // If deleted item was in breadcrumbs stack, pop it
                val idx = breadcrumbStack.indexOfFirst { it.id == deletedId }
                if (idx != -1) {
                    while (breadcrumbStack.size > idx) {
                        breadcrumbStack.removeLast()
                    }
                }
                selectedItemForDelete = null
            }
        )
    }

    // Add Multiple Items / Subtasks Dialog
    if (showMultiAddDialog) {
        AddMultipleItemsDialog(
            parentItem = targetParentForMultiAdd,
            listName = currentList.name,
            onDismiss = {
                showMultiAddDialog = false
                targetParentForMultiAdd = null
            },
            onConfirm = { items ->
                if (items.isNotEmpty()) {
                    ListRepository.addMultipleListItems(
                        listId = listId,
                        titles = items,
                        parentId = targetParentForMultiAdd?.id
                    )
                }
                showMultiAddDialog = false
                targetParentForMultiAdd = null
            }
        )
    }

    // Adjust Parent List Dialog
    if (showAdjustParentListDialog) {
        AdjustParentListDialog(
            currentList = currentList,
            allLists = allListsWithStats.map { it.list },
            onDismiss = { showAdjustParentListDialog = false },
            onConfirmMove = { targetListId ->
                ListRepository.moveListIntoList(currentList.id, targetListId)
                showAdjustParentListDialog = false
                onBack()
            }
        )
    }
}

/**
 * Scrollable Breadcrumbs Navigation Bar (Drill-down like Tasks)
 */
@Composable
fun ListBreadcrumbBar(
    listName: String,
    listIcon: String,
    breadcrumbStack: List<ListItem>,
    onNavigateBack: () -> Unit,
    onNavigateToCrumb: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        color = KairoSurfaceContainerHigh.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.18f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to previous level",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Root crumb: List Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigateToCrumb(-1) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    ListIconDisplay(
                        iconKey = listIcon,
                        tint = KairoPrimary,
                        modifier = Modifier.size(14.dp),
                        emojiSize = 14.sp
                    )
                    Text(
                        text = listName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = KairoPrimary
                    )
                }

                breadcrumbStack.forEachIndexed { index, parentItem ->
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF6B687C),
                        modifier = Modifier.size(14.dp)
                    )

                    val isLast = (index == breadcrumbStack.lastIndex)
                    Text(
                        text = parentItem.title,
                        fontSize = 12.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLast) MaterialTheme.colorScheme.onSurface else KairoPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onNavigateToCrumb(index) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Parent List Item Card (Matches Pic 1 List Overview Card):
 * - Reorder handle on the very left
 * - Checkbox to toggle parent and all subtasks
 * - 42.dp Icon container Box
 * - Bold title (16.sp)
 * - Subtitle: "X of Y completed"
 * - Progress percentage pill badge
 * - 3-dots menu: Add Subtask, Adjust Parent, Details, Edit, Delete
 * - Chevron drill-down indicator
 * - 6.dp linear progress indicator
 * - Tapping drills down into subtasks
 */
@Composable
private fun ParentListItemCard(
    item: ListItem,
    completedSubtasks: Int,
    totalSubtasks: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onToggleCompleted: (id: String) -> Unit,
    onDrillDown: () -> Unit,
    onShowDetails: () -> Unit,
    onAddSubtask: (parentId: String) -> Unit,
    onAdjustParent: (item: ListItem) -> Unit,
    onEdit: (item: ListItem) -> Unit,
    onDelete: (item: ListItem) -> Unit
) {
    var itemMenuExpanded by remember { mutableStateOf(false) }
    val percentage = if (totalSubtasks > 0) (completedSubtasks * 100) / totalSubtasks else 0
    val progressFloat = if (totalSubtasks > 0) completedSubtasks.toFloat() / totalSubtasks.toFloat() else 0f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = KairoSurfaceContainer,
        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onDrillDown)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder handle on the very left
            ListReorderHandle(
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onMoveToTop = onMoveToTop,
                onMoveToBottom = onMoveToBottom
            )

            // Checkbox to complete parent and all subtasks
            IconButton(
                onClick = { onToggleCompleted(item.id) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Toggle Complete",
                    tint = if (item.isCompleted) Color(0xFF10B981) else KairoOutlineVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Middle block: Icon + Name + % on top, progress bar below them
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KairoPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        ListIconDisplay(
                            iconKey = item.iconName,
                            tint = KairoPrimary,
                            modifier = Modifier.size(22.dp),
                            emojiSize = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$completedSubtasks/$totalSubtasks completed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (percentage == 100) Color(0xFF10B981).copy(alpha = 0.15f) else KairoPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (percentage == 100) Color(0xFF10B981).copy(alpha = 0.4f) else KairoPrimary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = "$percentage%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (percentage == 100) Color(0xFF10B981) else KairoPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progressFloat },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (percentage == 100) Color(0xFF10B981) else KairoPrimary,
                    trackColor = KairoSurfaceContainerLow
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 3-dots Menu for Parent Subtask Card on the far right
            Box {
                IconButton(
                    onClick = { itemMenuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Item Actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
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
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = KairoPrimary) },
                        onClick = {
                            itemMenuExpanded = false
                            onShowDetails()
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
}

/**
 * Leaf List Item Card (For items with no subtasks):
 * - Reorder handle on the very left
 * - Checkbox + Title + Notes/Date
 * - 3-dots Menu: Details, Add Subtask, Adjust Parent, Edit, Delete
 * - Clicking the card opens details sheet
 */
@Composable
private fun LeafListItemCard(
    item: ListItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onToggleCompleted: (id: String) -> Unit,
    onShowDetails: () -> Unit,
    onAddSubtask: (parentId: String) -> Unit,
    onAdjustParent: (item: ListItem) -> Unit,
    onEdit: (item: ListItem) -> Unit,
    onDelete: (item: ListItem) -> Unit
) {
    var itemMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = KairoSurfaceContainer,
        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onShowDetails)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder handle on the very left
            ListReorderHandle(
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onMoveToTop = onMoveToTop,
                onMoveToBottom = onMoveToBottom
            )

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

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!item.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.notes,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!item.targetDate.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
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

            // 3-dots Menu for every leaf item
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
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = KairoPrimary) },
                        onClick = {
                            itemMenuExpanded = false
                            onShowDetails()
                        }
                    )

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
}
