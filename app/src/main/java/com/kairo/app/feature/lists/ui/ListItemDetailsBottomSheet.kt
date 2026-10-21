package com.kairo.app.feature.lists.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.feature.lists.model.ListIconDisplay
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.feature.lists.model.ListWithStats
import com.kairo.app.feature.lists.model.TaskList
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerLow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * View-only Details Bottom Sheet for any ListItem.
 * Strictly read-only: displays comprehensive item metadata, creation & update timestamps,
 * completion status, subtasks count/progress, notes, and hierarchy.
 * No editing, adding, deleting, completing, or modifying actions can happen from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListItemDetailsBottomSheet(
    item: ListItem,
    allItemsInList: List<ListItem>,
    listName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    val parentItem = remember(item.parentId, allItemsInList) {
        allItemsInList.find { it.id == item.parentId }
    }
    val directSubtasks = remember(item.id, allItemsInList) {
        allItemsInList.filter { it.parentId == item.id }
    }
    val completedSubCount = directSubtasks.count { it.isCompleted }
    val totalSubCount = directSubtasks.size
    val activeSubCount = totalSubCount - completedSubCount
    val subtaskPercentage = if (totalSubCount > 0) ((completedSubCount.toFloat() / totalSubCount.toFloat()) * 100).toInt() else 0
    val subtaskProgressFloat = if (totalSubCount > 0) completedSubCount.toFloat() / totalSubCount.toFloat() else 0f

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header Row: Status Chip, Read-Only Badge, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status Badge (Completed / Active)
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (item.isCompleted) Color(0xFF10B981).copy(alpha = 0.15f) else KairoPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            if (item.isCompleted) Color(0xFF10B981).copy(alpha = 0.4f) else KairoPrimary.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (item.isCompleted) Color(0xFF10B981) else KairoPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (item.isCompleted) "Completed" else "Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isCompleted) Color(0xFF10B981) else KairoPrimary
                            )
                        }
                    }

                    // View-Only Pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = KairoSurfaceContainerLow,
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "View Only",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title & Icon Display (View Only, NO checkbox!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = item.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Hierarchy Context (List & Parent)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KairoSurfaceContainerLow,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Belongs to List
                    DetailInfoRow(
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        label = "Belongs to List",
                        value = listName
                    )

                    // Parent Item (if any)
                    if (parentItem != null) {
                        HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.12f))
                        DetailInfoRow(
                            icon = Icons.Default.AccountTree,
                            label = "Parent Task / Item",
                            value = "${parentItem.title} (${if (parentItem.isCompleted) "Completed" else "Active"})"
                        )
                    }

                    // Full Hierarchy Path (if item is nested)
                    val breadcrumbPath = remember(item.id, allItemsInList, listName) {
                        ListRepository.getBreadcrumbPath(item.id, allItemsInList, listName)
                    }
                    if (breadcrumbPath.size > 1) {
                        HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.12f))
                        DetailInfoRow(
                            icon = Icons.Default.AccountTree,
                            label = "Hierarchy Path",
                            value = breadcrumbPath.joinToString("  ›  ")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 2: Subtasks Summary (View-Only statistics)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KairoSurfaceContainerLow,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Subtasks",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (totalSubCount > 0) {
                            Text(
                                text = "$completedSubCount / $totalSubCount completed ($subtaskPercentage%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KairoPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (totalSubCount == 0) {
                        Text(
                            text = "0 subtasks (Leaf Item)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { subtaskProgressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (completedSubCount == totalSubCount) Color(0xFF10B981) else KairoPrimary,
                            trackColor = KairoOutlineVariant.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$totalSubCount total subtasks • $activeSubCount active, $completedSubCount completed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 3: Target Date (if any)
            if (!item.targetDate.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Target Date",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.targetDate,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Section 4: Notes (if any, View-Only)
            if (!item.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KairoSurfaceContainerLow,
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Notes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.notes,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 5: Activity & Timestamps (Created, Updated, Completed, Position)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KairoSurfaceContainerLow,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Activity & Timestamps",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.12f))

                    // Created At
                    DetailInfoRow(
                        icon = Icons.Default.Schedule,
                        label = "Created At",
                        value = dateFormat.format(Date(item.createdAt))
                    )

                    // Last Updated
                    DetailInfoRow(
                        icon = Icons.Default.Schedule,
                        label = "Last Updated",
                        value = dateFormat.format(Date(item.updatedAt))
                    )

                    // Completed At (or not completed)
                    DetailInfoRow(
                        icon = Icons.Default.CheckCircle,
                        label = "Completion Status",
                        value = if (item.isCompleted) {
                            if (item.completedAt != null) "Completed on ${dateFormat.format(Date(item.completedAt))}" else "Completed"
                        } else {
                            "Not completed yet"
                        }
                    )

                    // Order Position
                    DetailInfoRow(
                        icon = Icons.Default.FormatListNumbered,
                        label = "Order Position",
                        value = "#${item.position + 1}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Done / Close Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoSurfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f))
            ) {
                Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * View-only Details Bottom Sheet for a root TaskList.
 * Strictly read-only: displays comprehensive list metadata, total items, active vs completed counts,
 * progress percentage, creation timestamp, and last updated timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListDetailsBottomSheet(
    list: TaskList,
    listStats: ListWithStats,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val progressFloat = if (listStats.totalCount > 0) listStats.completedCount.toFloat() / listStats.totalCount.toFloat() else 0f

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header Row: Status Chip, View-Only Pill, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = KairoPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "List",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KairoPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = KairoSurfaceContainerLow,
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "View Only",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // List Title & Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairoPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    ListIconDisplay(
                        iconKey = list.iconName,
                        tint = KairoPrimary,
                        modifier = Modifier.size(22.dp),
                        emojiSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = list.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Items & Progress Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KairoSurfaceContainerLow,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Items & Progress",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (listStats.totalCount > 0) {
                            Text(
                                text = "${listStats.completedCount} / ${listStats.totalCount} completed (${listStats.progressPercentage}%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KairoPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (listStats.completedCount == listStats.totalCount && listStats.totalCount > 0) Color(0xFF10B981) else KairoPrimary,
                        trackColor = KairoOutlineVariant.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${listStats.totalCount} total items • ${listStats.activeCount} active, ${listStats.completedCount} completed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timestamps Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KairoSurfaceContainerLow,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "List Details & Timestamps",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.12f))

                    DetailInfoRow(
                        icon = Icons.Default.Schedule,
                        label = "Created At",
                        value = dateFormat.format(Date(list.createdAt))
                    )

                    DetailInfoRow(
                        icon = Icons.Default.Schedule,
                        label = "Last Updated",
                        value = dateFormat.format(Date(list.updatedAt))
                    )

                    DetailInfoRow(
                        icon = Icons.Default.FormatListNumbered,
                        label = "Order Position",
                        value = "#${list.position + 1}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Done / Close Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoSurfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f))
            ) {
                Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KairoPrimary,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
