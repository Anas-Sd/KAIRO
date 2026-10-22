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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.feature.lists.model.ListIconCatalog
import com.kairo.app.feature.lists.model.ListIconDisplay
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.feature.lists.ui.AddMultipleItemsDialog
import com.kairo.app.feature.lists.ui.AdjustParentListDialog
import com.kairo.app.ui.components.AppHeader
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
fun ListDetailScreen(
    listId: String,
    initialDrillDownItemId: String? = null,
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

    // Support deep-linking to an item (from global search or external link)
    LaunchedEffect(initialDrillDownItemId, listItems) {
        if (initialDrillDownItemId != null && breadcrumbStack.isEmpty()) {
            val target = listItems.find { it.id == initialDrillDownItemId }
            if (target != null) {
                val ancestors = ListRepository.getAncestorItems(target.id, listItems)
                breadcrumbStack.addAll(ancestors)
                val hasSubtasks = listItems.any { it.parentId == target.id }
                if (hasSubtasks) {
                    breadcrumbStack.add(target)
                }
            }
        }
    }

    val currentParent = remember(breadcrumbStack.toList(), listItems) {
        breadcrumbStack.lastOrNull()?.let { last -> listItems.find { it.id == last.id } }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val activeItems = remember(listItems) {
        listItems.filter { !it.isCompleted }
    }

    val completedItems = remember(listItems) {
        listItems.filter { it.isCompleted }
    }

    // Items scoped to current level (root if breadcrumbs empty, or children of currentParent)
    // When searching, recursively searches all items in the list!
    // Parent items (items that have subtasks) automatically float to the top above leaf items
    val scopedActiveItems = remember(activeItems, listItems, currentParent, searchQuery) {
        val base = if (searchQuery.isNotBlank()) {
            activeItems
        } else if (currentParent == null) {
            activeItems.filter { it.parentId == null }
        } else {
            activeItems.filter { it.parentId == currentParent.id }
        }
        val searched = if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.notes?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        val (parents, leaves) = searched.partition { item ->
            listItems.any { it.parentId == item.id }
        }
        parents.sortedBy { it.position } + leaves.sortedBy { it.position }
    }

    val scopedCompletedItems = remember(completedItems, listItems, currentParent, searchQuery) {
        val base = if (searchQuery.isNotBlank()) {
            completedItems
        } else if (currentParent == null) {
            completedItems.filter { it.parentId == null }
        } else {
            completedItems.filter { it.parentId == currentParent.id }
        }
        val searched = if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.notes?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        val (parents, leaves) = searched.partition { item ->
            listItems.any { it.parentId == item.id }
        }
        parents.sortedBy { it.position } + leaves.sortedBy { it.position }
    }

    // Two sections: Active (expanded) and Completed (collapsed by default)
    var isCompletedSectionExpanded by remember { mutableStateOf(false) }

    // Drag-to-reorder state for list items
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var localReorderItems by remember { mutableStateOf<List<ListItem>?>(null) }

    fun buildItemDragHandleModifier(item: ListItem, sectionItems: List<ListItem>): Modifier {
        return Modifier.pointerInput(item.id) {
            detectDragGestures(
                onDragStart = {
                    draggingItemId = item.id
                    dragOffsetY = 0f
                    localReorderItems = sectionItems
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y
                    val current = localReorderItems
                    val currentId = draggingItemId
                    if (current != null && currentId != null) {
                        val currentIndex = current.indexOfFirst { it.id == currentId }
                        val itemThreshold = 140f
                        if (dragOffsetY > itemThreshold && currentIndex in 0 until current.lastIndex) {
                            val mutable = current.toMutableList()
                            val targetIndex = currentIndex + 1
                            val itm = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, itm)
                            localReorderItems = mutable
                            dragOffsetY -= itemThreshold
                        } else if (dragOffsetY < -itemThreshold && currentIndex > 0) {
                            val mutable = current.toMutableList()
                            val targetIndex = currentIndex - 1
                            val itm = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, itm)
                            localReorderItems = mutable
                            dragOffsetY += itemThreshold
                        }
                    }
                },
                onDragEnd = {
                    localReorderItems?.let { reordered ->
                        ListRepository.reorderItems(currentList.id, reordered)
                    }
                    draggingItemId = null
                    dragOffsetY = 0f
                    localReorderItems = null
                },
                onDragCancel = {
                    draggingItemId = null
                    dragOffsetY = 0f
                    localReorderItems = null
                }
            )
        }
    }

    val context = LocalContext.current
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(emptySet<String>()) }
    var showCompleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAdjustParentGroupDialog by remember { mutableStateOf(false) }

    // Dialog states
    var showEditListDialog by remember { mutableStateOf(false) }
    var showAdjustParentListDialog by remember { mutableStateOf(false) }
    var showDeleteListDialog by remember { mutableStateOf(false) }
    var showListDetailsSheet by remember { mutableStateOf(false) }

    var selectedItemForDetails by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForEdit by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForAdjustParent by remember { mutableStateOf<ListItem?>(null) }
    var selectedItemForDelete by remember { mutableStateOf<ListItem?>(null) }
    var targetParentForMultiAdd by remember { mutableStateOf<ListItem?>(null) }
    var leafItemForAddSubtasks by remember { mutableStateOf<ListItem?>(null) }
    var showMultiAddDialog by remember { mutableStateOf(false) }

    // Back handling:
    // 0. If search active, close search
    // 1. If in selection mode, exit selection mode
    // 2. If inside breadcrumbs, pop one level
    // 3. Otherwise, exit to lists overview (onBack)
    BackHandler(enabled = true) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (isSelectionMode) {
            isSelectionMode = false
            selectedItemIds = emptySet()
        } else if (breadcrumbStack.isNotEmpty()) {
            breadcrumbStack.removeLast()
        } else {
            onBack()
        }
    }

    val headerTitle = currentParent?.title ?: currentList.name
    val headerIcon = if (currentParent != null) {
        if (currentParent.iconName.isNotBlank() && currentParent.iconName != "list") currentParent.iconName else "category"
    } else {
        currentList.iconName
    }

    val headerCompletedCount: Int
    val headerTotalCount: Int
    val headerPercentage: Int
    val headerProgressFloat: Float

    if (currentParent != null) {
        val directChildren = listItems.filter { it.parentId == currentParent.id }
        headerCompletedCount = directChildren.count { it.isCompleted }
        headerTotalCount = directChildren.size
        headerPercentage = if (headerTotalCount > 0) (headerCompletedCount * 100) / headerTotalCount else 0
        headerProgressFloat = if (headerTotalCount > 0) headerCompletedCount.toFloat() / headerTotalCount.toFloat() else 0f
    } else {
        headerCompletedCount = currentListStats.completedCount
        headerTotalCount = currentListStats.totalCount
        headerPercentage = currentListStats.progressPercentage
        headerProgressFloat = if (headerTotalCount > 0) headerCompletedCount.toFloat() / headerTotalCount.toFloat() else 0f
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KairoBackground)
            ) {
                // Main Header of Anas (date, search, settings, and 3 dots)
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedItemIds = emptySet()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel selection",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (selectedItemIds.isEmpty()) "Select items" else "${selectedItemIds.size} selected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val visibleItemIds = (scopedActiveItems + scopedCompletedItems).map { it.id }.toSet()
                        TextButton(onClick = {
                            selectedItemIds = if (selectedItemIds.size == visibleItemIds.size) {
                                emptySet()
                            } else {
                                visibleItemIds
                            }
                        }) {
                            Text(
                                text = if (selectedItemIds.size == visibleItemIds.size && visibleItemIds.isNotEmpty()) "Deselect All" else "Select All",
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
                        searchPlaceholder = if (currentParent != null) "Search in ${currentParent.title}..." else "Search in ${currentList.name}...",
                        onSettingsClick = { showSettingsDialog = true },
                        moreMenuContent = { dismissMenu ->
                            DropdownMenuItem(
                                text = { Text("Select") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SelectAll,
                                        contentDescription = null,
                                        tint = KairoPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    dismissMenu()
                                    isSelectionMode = true
                                    selectedItemIds = emptySet()
                                }
                            )

                            if (currentParent != null) {
                                DropdownMenuItem(
                                    text = { Text("Parent Details") },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = KairoPrimary) },
                                    onClick = {
                                        dismissMenu()
                                        selectedItemForDetails = currentParent
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Parent") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = KairoPrimary) },
                                    onClick = {
                                        dismissMenu()
                                        selectedItemForEdit = currentParent
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Adjust Parent") },
                                    leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = KairoPrimary) },
                                    onClick = {
                                        dismissMenu()
                                        selectedItemForAdjustParent = currentParent
                                    }
                                )
                                HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))
                                DropdownMenuItem(
                                    text = { Text("Delete Parent", color = KairoHighUrgent) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = KairoHighUrgent) },
                                    onClick = {
                                        dismissMenu()
                                        selectedItemForDelete = currentParent
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("List Details") },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = KairoPrimary) },
                                    onClick = {
                                        dismissMenu()
                                        showListDetailsSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit List") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = KairoPrimary) },
                                    onClick = {
                                        dismissMenu()
                                        showEditListDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Adjust Parent List") },
                                    leadingIcon = { Icon(Icons.Default.AccountTree, null, tint = KairoPrimary) },
                                    onClick = {
                                        dismissMenu()
                                        showAdjustParentListDialog = true
                                    }
                                )
                                HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))
                                DropdownMenuItem(
                                    text = { Text("Delete List", color = KairoHighUrgent) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = KairoHighUrgent) },
                                    onClick = {
                                        dismissMenu()
                                        showDeleteListDialog = true
                                    }
                                )
                            }
                        }
                    )
                }

                // List Sub-header with Back, Icon, Current Parent/List Name, Progress Count, and (+) button (NO 3-dots!)
                Surface(
                    color = KairoSurfaceContainer,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                    iconKey = headerIcon,
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(18.dp),
                                    emojiSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = headerTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (headerTotalCount > 0) {
                                    Text(
                                        text = "$headerCompletedCount of $headerTotalCount completed ($headerPercentage%)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Plus (+) button to add multiple items at once (NO 3-dots beside it!)
                            IconButton(onClick = {
                                targetParentForMultiAdd = currentParent
                                showMultiAddDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = if (currentParent != null) "Add Subtasks" else "Add Items",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Progress bar in the header itself just below title and actions
                        LinearProgressIndicator(
                            progress = { headerProgressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = if (headerPercentage == 100) Color(0xFF10B981) else KairoPrimary,
                            trackColor = if (headerTotalCount > 0) KairoSurfaceContainerLow else Color.Transparent
                        )
                    }
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(if (isSelectionMode) Modifier.padding(bottom = 76.dp) else Modifier)
            ) {
            // Breadcrumbs Navigation Bar (shown when drilled into subtasks)
            if (breadcrumbStack.isNotEmpty()) {
                ListBreadcrumbBar(
                    listName = currentList.name,
                    listIcon = currentList.iconName,
                    breadcrumbStack = breadcrumbStack,
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Main Scrollable Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active items
                val displayActiveItems = if (draggingItemId != null && scopedActiveItems.any { it.id == draggingItemId }) {
                    localReorderItems ?: scopedActiveItems
                } else {
                    scopedActiveItems
                }

                val displayCompletedItems = if (draggingItemId != null && scopedCompletedItems.any { it.id == draggingItemId }) {
                    localReorderItems ?: scopedCompletedItems
                } else {
                    scopedCompletedItems
                }

                if (displayActiveItems.isEmpty() && displayCompletedItems.isEmpty()) {
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
                    itemsIndexed(displayActiveItems, key = { _, item -> item.id }) { index, item ->
                        val isBeingDragged = (item.id == draggingItemId)
                        val childSubtasks = listItems.filter { it.parentId == item.id }
                        val hasSubtasks = childSubtasks.isNotEmpty()
                        val completedSubtasks = childSubtasks.count { it.isCompleted }
                        val breadcrumbPath = if (searchQuery.isNotBlank()) {
                            ListRepository.getBreadcrumbPath(item.id, listItems, currentList.name)
                        } else null

                        if (hasSubtasks) {
                            ParentListItemCard(
                                item = item,
                                completedSubtasks = completedSubtasks,
                                totalSubtasks = childSubtasks.size,
                                breadcrumbPath = breadcrumbPath,
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                dragHandleModifier = buildItemDragHandleModifier(item, displayActiveItems),
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItemIds.contains(item.id),
                                onToggleSelect = {
                                    selectedItemIds = if (selectedItemIds.contains(item.id)) selectedItemIds - item.id else selectedItemIds + item.id
                                },
                                onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                onDrillDown = {
                                    if (draggingItemId == null) {
                                        if (searchQuery.isNotBlank()) {
                                            val ancestors = ListRepository.getAncestorItems(item.id, listItems)
                                            breadcrumbStack.clear()
                                            breadcrumbStack.addAll(ancestors)
                                            breadcrumbStack.add(item)
                                            searchQuery = ""
                                            isSearchActive = false
                                        } else {
                                            breadcrumbStack.add(item)
                                        }
                                    }
                                },
                                onNavigateToLocation = {
                                    val ancestors = ListRepository.getAncestorItems(item.id, listItems)
                                    breadcrumbStack.clear()
                                    breadcrumbStack.addAll(ancestors)
                                    breadcrumbStack.add(item)
                                    searchQuery = ""
                                    isSearchActive = false
                                },
                                onShowDetails = {
                                    if (draggingItemId == null) {
                                        selectedItemForDetails = item
                                    }
                                },
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
                                breadcrumbPath = breadcrumbPath,
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                dragHandleModifier = buildItemDragHandleModifier(item, displayActiveItems),
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItemIds.contains(item.id),
                                onToggleSelect = {
                                    selectedItemIds = if (selectedItemIds.contains(item.id)) selectedItemIds - item.id else selectedItemIds + item.id
                                },
                                onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                onNavigateToLocation = if (searchQuery.isNotBlank()) {
                                    {
                                        val ancestors = ListRepository.getAncestorItems(item.id, listItems)
                                        breadcrumbStack.clear()
                                        breadcrumbStack.addAll(ancestors)
                                        searchQuery = ""
                                        isSearchActive = false
                                    }
                                } else null,
                                onShowDetails = {
                                    if (draggingItemId == null) {
                                        selectedItemForDetails = item
                                    }
                                },
                                onAddSubtask = { parentId ->
                                    val target = listItems.find { it.id == parentId } ?: item
                                    leafItemForAddSubtasks = target
                                },
                                onAdjustParent = { selectedItemForAdjustParent = it },
                                onEdit = { selectedItemForEdit = it },
                                onDelete = { selectedItemForDelete = it }
                            )
                        }
                    }

                    // 5. COMPLETED ITEMS SECTION (Collapsed by default)
                    if (displayCompletedItems.isNotEmpty()) {
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
                                            text = "Completed (${displayCompletedItems.size})",
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
                            itemsIndexed(displayCompletedItems, key = { _, completedItem -> completedItem.id }) { index, completedItem ->
                                val isBeingDragged = (completedItem.id == draggingItemId)
                                val childSubtasks = listItems.filter { it.parentId == completedItem.id }
                                val hasSubtasks = childSubtasks.isNotEmpty()
                                val completedCount = childSubtasks.count { it.isCompleted }
                                val breadcrumbPath = if (searchQuery.isNotBlank()) {
                                    ListRepository.getBreadcrumbPath(completedItem.id, listItems, currentList.name)
                                } else null

                                if (hasSubtasks) {
                                    ParentListItemCard(
                                        item = completedItem,
                                        completedSubtasks = completedCount,
                                        totalSubtasks = childSubtasks.size,
                                        breadcrumbPath = breadcrumbPath,
                                        isBeingDragged = isBeingDragged,
                                        dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                        dragHandleModifier = buildItemDragHandleModifier(completedItem, displayCompletedItems),
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedItemIds.contains(completedItem.id),
                                        onToggleSelect = {
                                            selectedItemIds = if (selectedItemIds.contains(completedItem.id)) selectedItemIds - completedItem.id else selectedItemIds + completedItem.id
                                        },
                                        onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                        onDrillDown = {
                                            if (draggingItemId == null) {
                                                if (searchQuery.isNotBlank()) {
                                                    val ancestors = ListRepository.getAncestorItems(completedItem.id, listItems)
                                                    breadcrumbStack.clear()
                                                    breadcrumbStack.addAll(ancestors)
                                                    breadcrumbStack.add(completedItem)
                                                    searchQuery = ""
                                                    isSearchActive = false
                                                } else {
                                                    breadcrumbStack.add(completedItem)
                                                }
                                            }
                                        },
                                        onNavigateToLocation = {
                                            val ancestors = ListRepository.getAncestorItems(completedItem.id, listItems)
                                            breadcrumbStack.clear()
                                            breadcrumbStack.addAll(ancestors)
                                            breadcrumbStack.add(completedItem)
                                            searchQuery = ""
                                            isSearchActive = false
                                        },
                                        onShowDetails = {
                                            if (draggingItemId == null) {
                                                selectedItemForDetails = completedItem
                                            }
                                        },
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
                                        breadcrumbPath = breadcrumbPath,
                                        isBeingDragged = isBeingDragged,
                                        dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                        dragHandleModifier = buildItemDragHandleModifier(completedItem, displayCompletedItems),
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedItemIds.contains(completedItem.id),
                                        onToggleSelect = {
                                            selectedItemIds = if (selectedItemIds.contains(completedItem.id)) selectedItemIds - completedItem.id else selectedItemIds + completedItem.id
                                        },
                                        onToggleCompleted = { ListRepository.toggleItemCompleted(it) },
                                        onNavigateToLocation = if (searchQuery.isNotBlank()) {
                                            {
                                                val ancestors = ListRepository.getAncestorItems(completedItem.id, listItems)
                                                breadcrumbStack.clear()
                                                breadcrumbStack.addAll(ancestors)
                                                searchQuery = ""
                                                isSearchActive = false
                                            }
                                        } else null,
                                        onShowDetails = {
                                            if (draggingItemId == null) {
                                                selectedItemForDetails = completedItem
                                            }
                                        },
                                        onAddSubtask = { parentId ->
                                            val target = listItems.find { it.id == parentId } ?: completedItem
                                            leafItemForAddSubtasks = target
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
                            color = if (selectedItemIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selectedItemIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = "${selectedItemIds.size}",
                                color = if (selectedItemIds.isNotEmpty()) KairoPrimary else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = if (selectedItemIds.size == 1) "item selected" else "items selected",
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
                                if (selectedItemIds.isEmpty()) {
                                    Toast.makeText(context, "No items selected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showCompleteConfirmDialog = true
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
                                if (selectedItemIds.isEmpty()) {
                                    Toast.makeText(context, "No items selected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showAdjustParentGroupDialog = true
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
                                if (selectedItemIds.isEmpty()) {
                                    Toast.makeText(context, "No items selected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showDeleteConfirmDialog = true
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

// Batch Complete Confirmation Dialog
if (showCompleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showCompleteConfirmDialog = false },
        title = {
            Text(
                text = "Complete ${selectedItemIds.size} item${if (selectedItemIds.size > 1) "s" else ""}?",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = "This will mark all ${selectedItemIds.size} selected item(s) as completed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val count = selectedItemIds.size
                    ListRepository.completeListItems(selectedItemIds)
                    isSelectionMode = false
                    selectedItemIds = emptySet()
                    showCompleteConfirmDialog = false
                    Toast.makeText(context, "$count item(s) completed", Toast.LENGTH_SHORT).show()
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
                onClick = { showCompleteConfirmDialog = false }
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

// Batch Delete Confirmation Dialog
if (showDeleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = {
            Text(
                text = "Delete ${selectedItemIds.size} item${if (selectedItemIds.size > 1) "s" else ""}?",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = "You are deleting ${selectedItemIds.size} item${if (selectedItemIds.size > 1) "s" else ""}. Are you sure you want to delete them permanently?",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val count = selectedItemIds.size
                    ListRepository.deleteListItems(selectedItemIds)
                    isSelectionMode = false
                    selectedItemIds = emptySet()
                    showDeleteConfirmDialog = false
                    Toast.makeText(context, "$count item(s) deleted", Toast.LENGTH_SHORT).show()
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
                onClick = { showDeleteConfirmDialog = false }
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

    // Item Details Bottom Sheet (Strictly View-Only)
    if (selectedItemForDetails != null) {
        ListItemDetailsBottomSheet(
            item = selectedItemForDetails!!,
            allItemsInList = listItems,
            listName = currentList.name,
            onDismiss = { selectedItemForDetails = null }
        )
    }

    // List Details Bottom Sheet (Strictly View-Only)
    if (showListDetailsSheet) {
        TaskListDetailsBottomSheet(
            list = currentList,
            listStats = currentListStats,
            onDismiss = { showListDetailsSheet = false }
        )
    }

    // Edit Item Dialog
    if (selectedItemForEdit != null) {
        val itemToEdit = selectedItemForEdit!!
        val hasSubtasks = listItems.any { it.parentId == itemToEdit.id }
        EditListItemDialog(
            item = itemToEdit,
            hasSubtasks = hasSubtasks,
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

    // Adjust Parent Group Dialog (Batch Move)
    if (showAdjustParentGroupDialog) {
        val selectedItems = remember(selectedItemIds, listItems) {
            listItems.filter { selectedItemIds.contains(it.id) }
        }
        AdjustParentGroupDialog(
            items = selectedItems,
            allItemsInList = listItems,
            onDismiss = { showAdjustParentGroupDialog = false },
            onMoveConfirmed = { newParentId, moveSubtasks ->
                val count = selectedItemIds.size
                ListRepository.adjustItemsParent(selectedItemIds, newParentId, moveSubtasks)
                val affectedInBreadcrumbs = breadcrumbStack.indexOfFirst { selectedItemIds.contains(it.id) }
                if (affectedInBreadcrumbs != -1) {
                    while (breadcrumbStack.size > affectedInBreadcrumbs) {
                        breadcrumbStack.removeLast()
                    }
                }
                isSelectionMode = false
                selectedItemIds = emptySet()
                showAdjustParentGroupDialog = false
                Toast.makeText(context, "$count item(s) moved", Toast.LENGTH_SHORT).show()
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

    // Add Subtasks to Leaf Node Dialog (Promoting to Parent with icon & subtasks)
    if (leafItemForAddSubtasks != null) {
        val targetLeaf = leafItemForAddSubtasks!!
        AddSubtasksToLeafDialog(
            leafItem = targetLeaf,
            onDismiss = { leafItemForAddSubtasks = null },
            onConfirm = { updatedTitle, updatedIconName, subtasks ->
                ListRepository.convertLeafToParentWithSubtasks(
                    itemId = targetLeaf.id,
                    updatedTitle = updatedTitle,
                    updatedIconName = updatedIconName,
                    subtaskTitles = subtasks
                )
                leafItemForAddSubtasks = null
                Toast.makeText(context, "Added ${subtasks.size} subtask(s)", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
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
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
    breadcrumbPath: List<String>? = null,
    isBeingDragged: Boolean = false,
    dragOffsetY: Float = 0f,
    dragHandleModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onToggleCompleted: (id: String) -> Unit,
    onDrillDown: () -> Unit,
    onNavigateToLocation: (() -> Unit)? = null,
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
        color = if (isSelected) KairoPrimary.copy(alpha = 0.08f) else if (isBeingDragged) KairoSurfaceContainerHigh else KairoSurfaceContainer,
        border = BorderStroke(
            1.2.dp,
            if (isSelected) KairoPrimary else if (isBeingDragged) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.22f)
        ),
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
            .clickable(onClick = {
                if (!isBeingDragged) {
                    if (isSelectionMode) onToggleSelect() else (onNavigateToLocation?.invoke() ?: onDrillDown())
                }
            })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: 6-dots Drag Indicator on top + Squircle Checkbox below it
            ListCardLeadingControl(
                isCompleted = if (isSelectionMode) isSelected else item.isCompleted,
                onToggleCompleted = {
                    if (isSelectionMode) onToggleSelect() else onToggleCompleted(item.id)
                },
                isBeingDragged = isBeingDragged,
                dragHandleModifier = if (isSelectionMode) Modifier else dragHandleModifier
            )

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

                        if (!breadcrumbPath.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KairoPrimary.copy(alpha = 0.08f))
                                    .clickable { onNavigateToLocation?.invoke() ?: onDrillDown() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
    breadcrumbPath: List<String>? = null,
    isBeingDragged: Boolean = false,
    dragOffsetY: Float = 0f,
    dragHandleModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onToggleCompleted: (id: String) -> Unit,
    onNavigateToLocation: (() -> Unit)? = null,
    onShowDetails: () -> Unit,
    onAddSubtask: (parentId: String) -> Unit,
    onAdjustParent: (item: ListItem) -> Unit,
    onEdit: (item: ListItem) -> Unit,
    onDelete: (item: ListItem) -> Unit
) {
    var itemMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) KairoPrimary.copy(alpha = 0.08f) else if (isBeingDragged) KairoSurfaceContainerHigh else KairoSurfaceContainer,
        border = BorderStroke(
            1.2.dp,
            if (isSelected) KairoPrimary else if (isBeingDragged) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isBeingDragged) {
                    Modifier
                        .zIndex(2f)
                        .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = {
                if (!isBeingDragged) {
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else if (onNavigateToLocation != null) {
                        onNavigateToLocation()
                    } else {
                        onShowDetails()
                    }
                }
            })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: 6-dots Drag Indicator on top + Squircle Checkbox below it
            ListCardLeadingControl(
                isCompleted = if (isSelectionMode) isSelected else item.isCompleted,
                onToggleCompleted = {
                    if (isSelectionMode) onToggleSelect() else onToggleCompleted(item.id)
                },
                isBeingDragged = isBeingDragged,
                dragHandleModifier = if (isSelectionMode) Modifier else dragHandleModifier
            )

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

                if (!breadcrumbPath.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KairoPrimary.copy(alpha = 0.08f))
                            .clickable { onNavigateToLocation?.invoke() ?: onShowDetails() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
