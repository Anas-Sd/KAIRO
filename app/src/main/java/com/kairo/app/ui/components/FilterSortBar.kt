package com.kairo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.FilterCriteria
import com.kairo.app.data.model.TaskSort
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh

@Composable
fun FilterSortBar(
    filterCriteria: FilterCriteria,
    activeSort: TaskSort,
    onOpenFilter: () -> Unit,
    onOpenSort: () -> Unit,
    onExportClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Filter Button
        Surface(
            modifier = Modifier
                .height(44.dp)
                .clickable { onOpenFilter() },
            shape = RoundedCornerShape(16.dp),
            color = KairoSurfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                if (filterCriteria.isActive) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = if (filterCriteria.isActive) KairoPrimary else Color(0xFFC8C4D9),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (filterCriteria.isActive) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(KairoPrimary, CircleShape)
                    )
                }
            }
        }

        // 2. Sort Selector Button
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clickable { onOpenSort() },
            shape = RoundedCornerShape(16.dp),
            color = KairoSurfaceContainerHigh,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = Color(0xFFC8C4D9),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = activeSort.shortName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
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

        // 3. Export Icon Button
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
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
