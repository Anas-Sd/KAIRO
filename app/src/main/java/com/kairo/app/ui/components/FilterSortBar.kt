package com.kairo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.TaskFilter
import com.kairo.app.data.model.TaskSort
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh

@Composable
fun FilterSortBar(
    activeFilter: TaskFilter,
    activeSort: TaskSort,
    onFilterSelected: (TaskFilter) -> Unit,
    onSortSelected: (TaskSort) -> Unit,
    onExportClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter button
        Box {
            Surface(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { filterMenuExpanded = true },
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHigh,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter",
                        tint = KairoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (activeFilter == TaskFilter.ALL) "Filter" else activeFilter.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Active indicator dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(KairoPrimary, CircleShape)
                    )
                }
            }

            DropdownMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { filterMenuExpanded = false }
            ) {
                TaskFilter.values().forEach { filter ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = filter.displayName,
                                fontWeight = if (filter == activeFilter) FontWeight.Bold else FontWeight.Normal,
                                color = if (filter == activeFilter) KairoPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onFilterSelected(filter)
                            filterMenuExpanded = false
                        }
                    )
                }
            }
        }

        // Sort selector
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable { sortMenuExpanded = true },
                shape = RoundedCornerShape(16.dp),
                color = KairoSurfaceContainerHigh,
                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Sort",
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Sort:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = activeSort.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                TaskSort.values().forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sort.displayName,
                                fontWeight = if (sort == activeSort) FontWeight.Bold else FontWeight.Normal,
                                color = if (sort == activeSort) KairoPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSortSelected(sort)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }

        // Export download button
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable { onExportClicked() },
            shape = RoundedCornerShape(16.dp),
            color = KairoSurfaceContainerHigh,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Export Tasks",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
