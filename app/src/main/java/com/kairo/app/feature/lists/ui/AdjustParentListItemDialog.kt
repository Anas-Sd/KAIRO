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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow

@Composable
fun AdjustParentListItemDialog(
    item: ListItem,
    allItemsInList: List<ListItem>,
    onDismiss: () -> Unit,
    onMoveConfirmed: (newParentId: String?, moveSubtasks: Boolean) -> Unit,
    onConvertToStandalone: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedParentId by remember { mutableStateOf<String?>(item.parentId) }
    var hasPickedTarget by remember { mutableStateOf(false) }
    var targetParentId by remember { mutableStateOf<String?>(null) }
    var showMoveOptionsModal by remember { mutableStateOf(false) }

    val hasDirectSubtasks = remember(item, allItemsInList) {
        allItemsInList.any { it.parentId == item.id }
    }

    // Exclude self and all descendants to prevent loops
    val loopForbiddenIds = remember(item, allItemsInList) {
        val descendants = ListRepository.getDescendantIds(item.id, allItemsInList)
        (descendants + item.id).toSet()
    }

    val eligibleParents = remember(allItemsInList, loopForbiddenIds, searchQuery) {
        allItemsInList.filter { candidate ->
            !loopForbiddenIds.contains(candidate.id) &&
                    (searchQuery.isBlank() || candidate.title.contains(searchQuery, ignoreCase = true))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = KairoSurfaceContainer,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(KairoPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Adjust Parent",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Move '${item.title}'",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search items...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable candidates list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Option: No Parent (Top-Level)
                    item {
                        val isRootSelected = selectedParentId == null
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isRootSelected) KairoPrimary.copy(alpha = 0.12f) else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isRootSelected) KairoPrimary.copy(alpha = 0.5f) else KairoOutlineVariant.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParentId = null }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isRootSelected,
                                    onClick = { selectedParentId = null },
                                    colors = RadioButtonDefaults.colors(selectedColor = KairoPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (isRootSelected) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Top-Level Item",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isRootSelected) KairoPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "No parent (stands as main item)",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Option: Convert to Standalone List (Below this list)
                    item {
                        val isStandaloneSelected = selectedParentId == "__STANDALONE__"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isStandaloneSelected) KairoPrimary.copy(alpha = 0.12f) else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isStandaloneSelected) KairoPrimary.copy(alpha = 0.5f) else KairoOutlineVariant.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParentId = "__STANDALONE__" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isStandaloneSelected,
                                    onClick = { selectedParentId = "__STANDALONE__" },
                                    colors = RadioButtonDefaults.colors(selectedColor = KairoPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    tint = if (isStandaloneSelected) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Standalone List",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isStandaloneSelected) KairoPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Make a main list placed below current list",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    items(eligibleParents) { candidate ->
                        val isCandidateSelected = selectedParentId == candidate.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCandidateSelected) KairoPrimary.copy(alpha = 0.12f) else KairoSurfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (isCandidateSelected) KairoPrimary.copy(alpha = 0.5f) else KairoOutlineVariant.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParentId = candidate.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isCandidateSelected,
                                    onClick = { selectedParentId = candidate.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = KairoPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isCandidateSelected) KairoPrimary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (candidate.parentId != null) {
                                        Text(
                                            text = "Sub-item",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Cancel & Move
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (selectedParentId == "__STANDALONE__") {
                                onConvertToStandalone()
                                onDismiss()
                                return@Button
                            }
                            if (selectedParentId == item.parentId) {
                                onDismiss()
                                return@Button
                            }
                            if (hasDirectSubtasks) {
                                targetParentId = selectedParentId
                                showMoveOptionsModal = true
                            } else {
                                onMoveConfirmed(selectedParentId, false)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KairoPrimary,
                            contentColor = Color(0xFF1E1F26)
                        )
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Modal when item has subtasks: "Move with all subtasks" or "Move only this item"
    if (showMoveOptionsModal) {
        AlertDialog(
            onDismissRequest = { showMoveOptionsModal = false },
            containerColor = KairoSurfaceContainer,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Move Subtasks?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "'${item.title}' has subtasks attached to it. Do you want to move its subtasks along with it, or keep them with the current parent?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMoveOptionsModal = false
                        onMoveConfirmed(targetParentId, true)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary, contentColor = Color(0xFF1E1F26))
                ) {
                    Text("Move with subtasks", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showMoveOptionsModal = false
                        onMoveConfirmed(targetParentId, false)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Move only this item", fontSize = 12.sp)
                }
            }
        )
    }
}
