package com.kairo.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.kairo.app.feature.lists.ui.AdjustParentListsDialog
import com.kairo.app.feature.lists.ui.CreateListDialog
import com.kairo.app.feature.lists.ui.ListCardLeadingControl
import com.kairo.app.feature.lists.ui.ListDetailScreen
import com.kairo.app.feature.lists.ui.TaskListDetailsBottomSheet
import kotlin.math.roundToInt
import com.kairo.app.ui.components.AppHeader
import com.kairo.app.ui.components.SectionHeader
import com.kairo.app.ui.components.SectionType
import com.kairo.app.ui.components.SettingsDialog
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow

@Composable
fun ListsScreen(
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ListRepository.fetchFromSupabase()
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedListIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var completedExpanded by remember { mutableStateOf(false) }
    var showDeleteWarningDialog by remember { mutableStateOf(false) }
    var showCompleteWarningDialog by remember { mutableStateOf(false) }
    var showAdjustParentListsDialog by remember { mutableStateOf(false) }

    // Navigation state to Dedicated List Details Page
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var initialDrillDownItemId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            selectedListId = null
            initialDrillDownItemId = null
            isSearchActive = false
            searchQuery = ""
            isSelectionMode = false
            selectedListIds = emptySet()
            showAdjustParentListsDialog = false
            listState.animateScrollToItem(0)
        }
    }

    // Dialog state for creating / editing list
    var showCreateDialog by remember { mutableStateOf(false) }
    var listForDetails by remember { mutableStateOf<ListWithStats?>(null) }
    var listToEdit by remember { mutableStateOf<TaskList?>(null) }
    var listToAdjustParent by remember { mutableStateOf<TaskList?>(null) }
    var listToDelete by remember { mutableStateOf<TaskList?>(null) }

    val allListsWithStats by ListRepository.listsWithStats.collectAsState()
    val allItems by ListRepository.items.collectAsState()

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedListIds = emptySet()
        showAdjustParentListsDialog = false
    }

    BackHandler(enabled = !isSelectionMode && isSearchActive) {
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
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(KairoBackground)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedListIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (selectedListIds.isEmpty()) "Select lists" else "${selectedListIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Select All / Deselect All
                    TextButton(onClick = {
                        selectedListIds = if (selectedListIds.size == allListsWithStats.size) {
                            emptySet()
                        } else {
                            allListsWithStats.map { it.list.id }.toSet()
                        }
                    }) {
                        Text(
                            text = if (selectedListIds.size == allListsWithStats.size) "Deselect All" else "Select All",
                            color = KairoPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
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
                    onSettingsClick = { showSettingsDialog = true },
                    moreMenuContent = { dismissMenu ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Select",
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                dismissMenu()
                                isSelectionMode = true
                                selectedListIds = emptySet()
                            }
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
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
            }
        },
        containerColor = KairoBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
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
                    // Search Results: Active Lists + Completed Lists (collapsed section) + Items with breadcrumbs
                    val searchActiveLists = matchingLists.filter { !it.isCompleted }
                    val searchCompletedLists = matchingLists.filter { it.isCompleted }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (searchActiveLists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Lists (${searchActiveLists.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            itemsIndexed(searchActiveLists, key = { _, stats -> "search_active_${stats.list.id}" }) { _, stats ->
                                ListOverviewCard(
                                    stats = stats,
                                    isBeingDragged = false,
                                    dragOffsetY = 0f,
                                    dragHandleModifier = Modifier,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedListIds.contains(stats.list.id),
                                    onSelectionChange = { checked ->
                                        selectedListIds = if (checked) selectedListIds + stats.list.id else selectedListIds - stats.list.id
                                    },
                                    onToggleComplete = {
                                        ListRepository.setListCompleted(stats.list.id, !stats.isCompleted)
                                    },
                                    onClick = {
                                        selectedListId = stats.list.id
                                    },
                                    onDetails = { listForDetails = stats },
                                    onEdit = { listToEdit = stats.list },
                                    onAdjustParent = { listToAdjustParent = stats.list },
                                    onDelete = { listToDelete = stats.list },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (searchCompletedLists.isNotEmpty()) {
                            val isSearchCompletedAllSelected = searchCompletedLists.all { selectedListIds.contains(it.list.id) }
                            item(key = "search_section_completed_lists_header") {
                                SectionHeader(
                                    title = "Completed",
                                    countText = "${searchCompletedLists.size} completed",
                                    sectionType = SectionType.COMPLETED,
                                    isExpanded = completedExpanded,
                                    onToggle = { completedExpanded = !completedExpanded },
                                    showCheckbox = isSelectionMode,
                                    isChecked = isSearchCompletedAllSelected,
                                    onCheckedChange = { checked ->
                                        val ids = searchCompletedLists.map { it.list.id }.toSet()
                                        selectedListIds = if (checked) selectedListIds + ids else selectedListIds - ids
                                    }
                                )
                            }

                            if (completedExpanded) {
                                itemsIndexed(searchCompletedLists, key = { _, stats -> "search_completed_${stats.list.id}" }) { _, stats ->
                                    ListOverviewCard(
                                        stats = stats,
                                        isBeingDragged = false,
                                        dragOffsetY = 0f,
                                        dragHandleModifier = Modifier,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedListIds.contains(stats.list.id),
                                        onSelectionChange = { checked ->
                                             selectedListIds = if (checked) selectedListIds + stats.list.id else selectedListIds - stats.list.id
                                        },
                                        onToggleComplete = {
                                            ListRepository.setListCompleted(stats.list.id, !stats.isCompleted)
                                        },
                                        onClick = {
                                            selectedListId = stats.list.id
                                        },
                                        onDetails = { listForDetails = stats },
                                        onEdit = { listToEdit = stats.list },
                                        onAdjustParent = { listToAdjustParent = stats.list },
                                        onDelete = { listToDelete = stats.list },
                                        modifier = Modifier.animateItem()
                                    )
                                }
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
                    // Main List View: Active Lists (with drag reorder) + Collapsed Completed Lists
                    val activeLists = matchingLists.filter { !it.isCompleted }
                    val completedLists = matchingLists.filter { it.isCompleted }
                    val displayActiveLists = if (draggingListId != null) (localReorderLists ?: activeLists) else activeLists

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (displayActiveLists.isNotEmpty()) {
                            itemsIndexed(displayActiveLists, key = { _, stats -> stats.list.id }) { index, stats ->
                                val isBeingDragged = (stats.list.id == draggingListId)
                                ListOverviewCard(
                                    stats = stats,
                                    isBeingDragged = isBeingDragged,
                                    dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                    dragHandleModifier = if (!isSelectionMode) buildListDragHandleModifier(stats, displayActiveLists) else Modifier,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedListIds.contains(stats.list.id),
                                    onSelectionChange = { checked ->
                                        selectedListIds = if (checked) selectedListIds + stats.list.id else selectedListIds - stats.list.id
                                    },
                                    onToggleComplete = {
                                        ListRepository.setListCompleted(stats.list.id, !stats.isCompleted)
                                    },
                                    onClick = {
                                        if (draggingListId == null) {
                                            selectedListId = stats.list.id
                                        }
                                    },
                                    onDetails = { listForDetails = stats },
                                    onEdit = { listToEdit = stats.list },
                                    onAdjustParent = { listToAdjustParent = stats.list },
                                    onDelete = { listToDelete = stats.list },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        if (completedLists.isNotEmpty()) {
                            val isCompletedAllSelected = completedLists.all { selectedListIds.contains(it.list.id) }
                            item(key = "section_completed_lists_header") {
                                SectionHeader(
                                    title = "Completed",
                                    countText = "${completedLists.size} completed",
                                    sectionType = SectionType.COMPLETED,
                                    isExpanded = completedExpanded,
                                    onToggle = { completedExpanded = !completedExpanded },
                                    showCheckbox = isSelectionMode,
                                    isChecked = isCompletedAllSelected,
                                    onCheckedChange = { checked ->
                                        val ids = completedLists.map { it.list.id }.toSet()
                                        selectedListIds = if (checked) selectedListIds + ids else selectedListIds - ids
                                    }
                                )
                            }

                            if (completedExpanded) {
                                itemsIndexed(completedLists, key = { _, stats -> stats.list.id }) { _, stats ->
                                    ListOverviewCard(
                                        stats = stats,
                                        isBeingDragged = false,
                                        dragOffsetY = 0f,
                                        dragHandleModifier = Modifier,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedListIds.contains(stats.list.id),
                                        onSelectionChange = { checked ->
                                            selectedListIds = if (checked) selectedListIds + stats.list.id else selectedListIds - stats.list.id
                                        },
                                        onToggleComplete = {
                                            ListRepository.setListCompleted(stats.list.id, !stats.isCompleted)
                                        },
                                        onClick = {
                                            selectedListId = stats.list.id
                                        },
                                        onDetails = { listForDetails = stats },
                                        onEdit = { listToEdit = stats.list },
                                        onAdjustParent = { listToAdjustParent = stats.list },
                                        onDelete = { listToDelete = stats.list },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }

            // Selection Bottom Action Bar (Complete & Delete)
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = KairoSurfaceContainerHigh,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Selected count badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedListIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selectedListIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f)
                                )
                            ) {
                                Text(
                                    text = "${selectedListIds.size}",
                                    color = if (selectedListIds.isNotEmpty()) KairoPrimary else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = if (selectedListIds.size == 1) "list selected" else "lists selected",
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        // Right: Actions (Complete, Adjust & Delete)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Complete Button
                            Surface(
                                onClick = {
                                    if (selectedListIds.isEmpty()) {
                                        Toast.makeText(context, "No lists selected", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showCompleteWarningDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = KairoSecondary.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, KairoSecondary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = KairoSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Complete",
                                        color = KairoSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Adjust Parent Button
                            Surface(
                                onClick = {
                                    if (selectedListIds.isEmpty()) {
                                        Toast.makeText(context, "No lists selected", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showAdjustParentListsDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = KairoPrimary.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountTree,
                                        contentDescription = null,
                                        tint = KairoPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Adjust",
                                        color = KairoPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Delete Button
                            Surface(
                                onClick = {
                                    if (selectedListIds.isEmpty()) {
                                        Toast.makeText(context, "No lists selected", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showDeleteWarningDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = KairoError.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, KairoError.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = KairoError,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Delete",
                                        color = KairoError,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Settings Modal
    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
    }

    // List Details Bottom Sheet (Strictly View-Only)
    if (listForDetails != null) {
        TaskListDetailsBottomSheet(
            list = listForDetails!!.list,
            listStats = listForDetails!!,
            onDismiss = { listForDetails = null }
        )
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

    // Adjust Parent List Dialog (Single list via card action)
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

    // Adjust Parent for Selected Lists Dialog (Batch Move via Selection)
    if (showAdjustParentListsDialog) {
        val selectedLists = allListsWithStats
            .filter { selectedListIds.contains(it.list.id) }
            .map { it.list }
        AdjustParentListsDialog(
            selectedLists = selectedLists,
            allLists = allListsWithStats.map { it.list },
            onDismiss = { showAdjustParentListsDialog = false },
            onConfirmMove = { targetListId ->
                val count = selectedListIds.size
                val targetList = allListsWithStats.find { it.list.id == targetListId }?.list
                ListRepository.moveListsIntoList(selectedListIds, targetListId)
                selectedListIds = emptySet()
                isSelectionMode = false
                showAdjustParentListsDialog = false
                val targetName = targetList?.name ?: "target list"
                Toast.makeText(context, "$count list(s) moved under $targetName", Toast.LENGTH_SHORT).show()
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

    // Batch Delete Confirmation Dialog
    if (showDeleteWarningDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteWarningDialog = false },
            title = {
                Text(
                    text = "Delete ${selectedListIds.size} list${if (selectedListIds.size > 1) "s" else ""}?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "You are deleting ${selectedListIds.size} list${if (selectedListIds.size > 1) "s" else ""} and all their items. Are you sure you want to delete them permanently?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = selectedListIds.size
                        ListRepository.deleteLists(selectedListIds)
                        isSelectionMode = false
                        selectedListIds = emptySet()
                        showDeleteWarningDialog = false
                        Toast.makeText(context, "$count list(s) deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Yes, Delete",
                        color = KairoError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteWarningDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Batch Complete Confirmation Dialog
    if (showCompleteWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteWarningDialog = false },
            title = {
                Text(
                    text = "Complete ${selectedListIds.size} list${if (selectedListIds.size > 1) "s" else ""}?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This will mark all ${selectedListIds.size} selected list(s) and their items as completed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = selectedListIds.size
                        ListRepository.completeLists(selectedListIds)
                        isSelectionMode = false
                        selectedListIds = emptySet()
                        showCompleteWarningDialog = false
                        Toast.makeText(context, "$count list(s) completed", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Yes, Complete",
                        color = KairoSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompleteWarningDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ListOverviewCard(
    stats: ListWithStats,
    isBeingDragged: Boolean = false,
    dragOffsetY: Float = 0f,
    dragHandleModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit = {},
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onDetails: () -> Unit = {},
    onEdit: () -> Unit,
    onAdjustParent: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isAllCompleted = stats.isCompleted
    val progressFloat = if (stats.totalCount > 0) {
        stats.completedCount.toFloat() / stats.totalCount.toFloat()
    } else if (isAllCompleted) 1f else 0f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isBeingDragged) KairoSurfaceContainerHigh else KairoSurfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isBeingDragged) KairoPrimary
            else if (isSelectionMode && isSelected) KairoPrimary.copy(alpha = 0.5f)
            else KairoOutlineVariant.copy(alpha = 0.22f)
        ),
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
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (isSelectionMode) {
                    onSelectionChange(!isSelected)
                } else {
                    onClick()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: 6-dots Drag Indicator on top + Squircle Checkbox below it (or Selection checkbox)
            ListCardLeadingControl(
                isCompleted = isAllCompleted,
                onToggleCompleted = onToggleComplete,
                isBeingDragged = isBeingDragged,
                dragHandleModifier = dragHandleModifier,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onSelectionChange = onSelectionChange
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

            // 3-dots Menu on the far right (hidden in selection mode)
            if (!isSelectionMode) {
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
                            text = { Text("Details") },
                            leadingIcon = { Icon(Icons.Default.Info, null, tint = KairoPrimary) },
                            onClick = {
                                menuExpanded = false
                                onDetails()
                            }
                        )

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
}
