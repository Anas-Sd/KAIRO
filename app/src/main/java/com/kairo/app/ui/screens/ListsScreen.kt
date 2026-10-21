package com.kairo.app.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.feature.lists.model.ListIconCatalog
import com.kairo.app.feature.lists.model.ListIconDisplay
import com.kairo.app.feature.lists.model.ListWithStats
import com.kairo.app.feature.lists.model.TaskList
import com.kairo.app.feature.lists.ui.AdjustParentListDialog
import com.kairo.app.feature.lists.ui.CreateListDialog
import com.kairo.app.feature.lists.ui.ListDetailScreen
import com.kairo.app.feature.lists.ui.ListReorderHandle
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

    // Dialog state for creating / editing list
    var showCreateDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<TaskList?>(null) }
    var listToAdjustParent by remember { mutableStateOf<TaskList?>(null) }
    var listToDelete by remember { mutableStateOf<TaskList?>(null) }

    val allListsWithStats by ListRepository.listsWithStats.collectAsState()
    val allItems by ListRepository.items.collectAsState()

    // If a list is selected, display dedicated ListDetailScreen
    if (selectedListId != null) {
        ListDetailScreen(
            listId = selectedListId!!,
            onBack = { selectedListId = null }
        )
        return
    }

    val filteredLists = remember(allListsWithStats, allItems, searchQuery) {
        if (searchQuery.isBlank()) {
            allListsWithStats
        } else {
            allListsWithStats.filter { stats ->
                val listMatches = stats.list.name.contains(searchQuery, ignoreCase = true)
                val itemMatches = allItems.any { it.listId == stats.list.id && it.title.contains(searchQuery, ignoreCase = true) }
                listMatches || itemMatches
            }
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
            if (filteredLists.isEmpty()) {
                // Empty state
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
                            text = if (searchQuery.isNotBlank()) "No matching lists found" else "No lists yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "Try a different search term" else "Create your first list to organize projects, packing, or groceries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )

                        if (searchQuery.isBlank()) {
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
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredLists, key = { _, stats -> stats.list.id }) { index, stats ->
                        ListOverviewCard(
                            stats = stats,
                            canMoveUp = index > 0,
                            canMoveDown = index < filteredLists.size - 1,
                            onMoveUp = { ListRepository.moveListUp(stats.list.id) },
                            onMoveDown = { ListRepository.moveListDown(stats.list.id) },
                            onMoveToTop = { ListRepository.moveListToTop(stats.list.id) },
                            onMoveToBottom = { ListRepository.moveListToBottom(stats.list.id) },
                            onToggleComplete = {
                                val isAllDone = stats.totalCount > 0 && stats.completedCount == stats.totalCount
                                ListRepository.checkAllItems(stats.list.id, !isAllDone)
                            },
                            onClick = { selectedListId = stats.list.id },
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAdjustParent: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isAllCompleted = stats.totalCount > 0 && stats.completedCount == stats.totalCount

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = KairoSurfaceContainer,
        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                // Bi-directional completion checkbox
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isAllCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle Complete",
                        tint = if (isAllCompleted) Color(0xFF10B981) else KairoOutlineVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAllCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isAllCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stats.completedCount} of ${stats.totalCount} completed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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

            Spacer(modifier = Modifier.height(12.dp))

            val progressFloat = if (stats.totalCount > 0) {
                stats.completedCount.toFloat() / stats.totalCount.toFloat()
            } else 0f

            LinearProgressIndicator(
                progress = { progressFloat },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (stats.progressPercentage == 100) Color(0xFF10B981) else KairoPrimary,
                trackColor = KairoSurfaceContainerLow
            )
        }
    }
}
