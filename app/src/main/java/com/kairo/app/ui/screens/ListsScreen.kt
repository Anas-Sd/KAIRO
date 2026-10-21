package com.kairo.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.feature.lists.model.ListIconCatalog
import com.kairo.app.feature.lists.model.ListIconDisplay
import com.kairo.app.feature.lists.model.ListWithStats
import com.kairo.app.feature.lists.model.TaskList
import com.kairo.app.feature.lists.ui.AdjustParentListDialog
import com.kairo.app.feature.lists.ui.CreateListDialog
import com.kairo.app.feature.lists.ui.ListCardLeadingControl
import com.kairo.app.feature.lists.ui.ListDetailScreen
import kotlin.math.roundToInt
import com.kairo.app.ui.components.AppHeader
import com.kairo.app.ui.components.SettingsDialog
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow

@Composable
fun ListsScreen(modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        ListRepository.fetchFromSupabase()
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Navigation state to Dedicated List Details Page
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var initialDrillDownItemId by remember { mutableStateOf<String?>(null) }

    // Dialog state for creating / editing list
    var showCreateDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<TaskList?>(null) }
    var listToAdjustParent by remember { mutableStateOf<TaskList?>(null) }
    var listToDelete by remember { mutableStateOf<TaskList?>(null) }

    val allListsWithStats by ListRepository.listsWithStats.collectAsState()
    val allItems by ListRepository.items.collectAsState()

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    // If a list is selected, display dedicated ListDetailScreen
    if (selectedListId != null) {
        ListDetailScreen(
            listId = selectedListId!!,
            initialDrillDownItemId = initialDrillDownItemId,
            onBack = {
                selectedListId = null
                initialDrillDownItemId = null
            }
        )
        return
    }

    val matchingLists = remember(allListsWithStats, searchQuery) {
        if (searchQuery.isBlank()) {
            allListsWithStats
        } else {
            allListsWithStats.filter { stats ->
                stats.list.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val matchingItems = remember(allItems, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allItems.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.notes?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    // Drag-to-reorder state for main lists
    var draggingListId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var localReorderLists by remember { mutableStateOf<List<ListWithStats>?>(null) }

    fun buildListDragHandleModifier(stats: ListWithStats, currentLists: List<ListWithStats>): Modifier {
        return Modifier.pointerInput(stats.list.id) {
            detectDragGestures(
                onDragStart = {
                    draggingListId = stats.list.id
                    dragOffsetY = 0f
                    localReorderLists = currentLists
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y
                    val current = localReorderLists
                    val currentId = draggingListId
                    if (current != null && currentId != null) {
                        val currentIndex = current.indexOfFirst { it.list.id == currentId }
                        val itemThreshold = 140f
                        if (dragOffsetY > itemThreshold && currentIndex in 0 until current.lastIndex) {
                            val mutable = current.toMutableList()
                            val targetIndex = currentIndex + 1
                            val item = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, item)
                            localReorderLists = mutable
                            dragOffsetY -= itemThreshold
                        } else if (dragOffsetY < -itemThreshold && currentIndex > 0) {
                            val mutable = current.toMutableList()
                            val targetIndex = currentIndex - 1
                            val item = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, item)
                            localReorderLists = mutable
                            dragOffsetY += itemThreshold
                        }
                    }
                },
                onDragEnd = {
                    localReorderLists?.let { reordered ->
                        ListRepository.reorderLists(reordered.map { it.list })
                    }
                    draggingListId = null
                    dragOffsetY = 0f
                    localReorderLists = null
                },
                onDragCancel = {
                    draggingListId = null
                    dragOffsetY = 0f
                    localReorderLists = null
                }
            )
        }
    }

    Scaffold(
        topBar = {
            AppHeader(
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                onCloseSearch = {
                    isSearchActive = false
                    searchQuery = ""
                },
                searchPlaceholder = "Search lists & items...",
                onSettingsClick = { showSettingsDialog = true }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = KairoPrimary,
                contentColor = Color(0xFF1E1F26),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 12.dp, end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New List",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = KairoBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val isSearching = searchQuery.isNotBlank()
            val hasSearchMatches = matchingLists.isNotEmpty() || matchingItems.isNotEmpty()

            if (isSearching && !hasSearchMatches) {
                // Empty search results state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(KairoPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No matching lists or items found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Try searching for a different keyword",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (!isSearching && allListsWithStats.isEmpty()) {
                // Empty lists state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(KairoPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No lists yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Create your first list to organize projects, packing, or groceries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary, contentColor = Color(0xFF1E1F26))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create List", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else if (isSearching) {
                // Search Results: Lists section + Items section with breadcrumbs!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (matchingLists.isNotEmpty()) {
                        if (matchingItems.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Lists (${matchingLists.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(matchingLists, key = { _, stats -> "list_${stats.list.id}" }) { _, stats ->
                            ListOverviewCard(
                                stats = stats,
                                isBeingDragged = false,
                                dragOffsetY = 0f,
                                dragHandleModifier = Modifier,
                                onToggleComplete = {
                                    val isAllDone = stats.totalCount > 0 && stats.completedCount == stats.totalCount
                                    ListRepository.checkAllItems(stats.list.id, !isAllDone)
                                },
                                onClick = {
                                    selectedListId = stats.list.id
                                },
                                onEdit = { listToEdit = stats.list },
                                onAdjustParent = { listToAdjustParent = stats.list },
                                onDelete = { listToDelete = stats.list }
                            )
                        }
                    }

                    if (matchingItems.isNotEmpty()) {
                        item {
                            if (matchingLists.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = "Items (${matchingItems.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        itemsIndexed(matchingItems, key = { _, item -> "item_${item.id}" }) { _, item ->
                            val breadcrumbPath = remember(item.id, allItems) {
                                ListRepository.getBreadcrumbPath(item.id, allItems)
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = KairoSurfaceContainer,
                                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedListId = item.listId
                                        initialDrillDownItemId = item.id
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Squircle Checkbox
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (item.isCompleted) Color(0xFF10B981) else Color.Transparent,
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (item.isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { ListRepository.toggleItemCompleted(item.id) }
                                    ) {
                                        if (item.isCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (breadcrumbPath.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountTree,
                                                    contentDescription = null,
                                                    tint = KairoPrimary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = breadcrumbPath.joinToString("  ›  "),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = KairoPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

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
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Go to item",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            } else {
                val displayLists = if (draggingListId != null) (localReorderLists ?: matchingLists) else matchingLists
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(displayLists, key = { _, stats -> stats.list.id }) { index, stats ->
                        val isBeingDragged = (stats.list.id == draggingListId)
                        ListOverviewCard(
                            stats = stats,
                            isBeingDragged = isBeingDragged,
                            dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                            dragHandleModifier = buildListDragHandleModifier(stats, displayLists),
                            onToggleComplete = {
                                val isAllDone = stats.totalCount > 0 && stats.completedCount == stats.totalCount
                                ListRepository.checkAllItems(stats.list.id, !isAllDone)
                            },
                            onClick = {
                                if (draggingListId == null) {
                                    selectedListId = stats.list.id
                                }
                            },
                            onEdit = { listToEdit = stats.list },
                            onAdjustParent = { listToAdjustParent = stats.list },
                            onDelete = { listToDelete = stats.list }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // Settings Modal
    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
    }

    // Create List Dialog
    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, iconName, initialSubtasks ->
                val newList = ListRepository.createList(name, iconName, initialSubtasks)
                showCreateDialog = false
                // Seamlessly open the new list!
                selectedListId = newList.id
            }
        )
    }

    // Edit List Dialog
    if (listToEdit != null) {
        CreateListDialog(
            initialList = listToEdit,
            onDismiss = { listToEdit = null },
            onConfirm = { name, iconName, _ ->
                ListRepository.updateList(listToEdit!!.id, name, iconName)
                listToEdit = null
            }
        )
    }

    // Adjust Parent List Dialog
    if (listToAdjustParent != null) {
        AdjustParentListDialog(
            currentList = listToAdjustParent!!,
            allLists = allListsWithStats.map { it.list },
            onDismiss = { listToAdjustParent = null },
            onConfirmMove = { targetListId ->
                ListRepository.moveListIntoList(listToAdjustParent!!.id, targetListId)
                listToAdjustParent = null
            }
        )
    }

    // Delete List Confirmation Dialog
    if (listToDelete != null) {
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            containerColor = KairoSurfaceContainer,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Delete '${listToDelete!!.name}'?",
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
                        ListRepository.deleteList(listToDelete!!.id)
                        listToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KairoHighUrgent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { listToDelete = null },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
private fun ListOverviewCard(
    stats: ListWithStats,
    isBeingDragged: Boolean = false,
    dragOffsetY: Float = 0f,
    dragHandleModifier: Modifier = Modifier,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAdjustParent: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isAllCompleted = stats.totalCount > 0 && stats.completedCount == stats.totalCount
    val progressFloat = if (stats.totalCount > 0) {
        stats.completedCount.toFloat() / stats.totalCount.toFloat()
    } else 0f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isBeingDragged) KairoSurfaceContainerHigh else KairoSurfaceContainer,
        border = BorderStroke(1.dp, if (isBeingDragged) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.22f)),
        modifier = Modifier
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: 6-dots Drag Indicator on top + Squircle Checkbox below it
            ListCardLeadingControl(
                isCompleted = isAllCompleted,
                onToggleCompleted = onToggleComplete,
                isBeingDragged = isBeingDragged,
                dragHandleModifier = dragHandleModifier
            )

            // Main content block with Icon + Text + % on top, and progress bar below them
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
                            iconKey = stats.list.iconName,
                            tint = KairoPrimary,
                            modifier = Modifier.size(22.dp),
                            emojiSize = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stats.list.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (isAllCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${stats.completedCount}/${stats.totalCount} completed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (stats.progressPercentage == 100) Color(0xFF10B981).copy(alpha = 0.15f) else KairoPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (stats.progressPercentage == 100) Color(0xFF10B981).copy(alpha = 0.4f) else KairoPrimary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = "${stats.progressPercentage}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.progressPercentage == 100) Color(0xFF10B981) else KairoPrimary,
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
                    color = if (stats.progressPercentage == 100) Color(0xFF10B981) else KairoPrimary,
                    trackColor = KairoSurfaceContainerLow
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 3-dots Menu on the far right
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(KairoSurfaceContainerHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit List") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = KairoPrimary) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Adjust Parent") },
                        leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = KairoPrimary) },
                        onClick = {
                            menuExpanded = false
                            onAdjustParent()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Delete List", color = KairoHighUrgent) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = KairoHighUrgent) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
