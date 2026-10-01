package com.kairo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.Task
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh

@Composable
fun BreadcrumbBar(
    breadcrumbStack: List<Task>,
    onNavigateBack: () -> Unit,
    onNavigateToCrumb: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (breadcrumbStack.isEmpty()) return

    val scrollState = rememberScrollState()

    Surface(
        color = KairoSurfaceContainerHigh.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick 1-step back icon
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Scrollable breadcrumbs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Root crumb: "All Tasks"
                Text(
                    text = "All Tasks",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = KairoPrimary,
                    modifier = Modifier
                        .clickable { onNavigateToCrumb(-1) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )

                breadcrumbStack.forEachIndexed { index, parentTask ->
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF6B687C),
                        modifier = Modifier.size(14.dp)
                    )

                    val isLast = (index == breadcrumbStack.lastIndex)
                    Text(
                        text = parentTask.title,
                        fontSize = 13.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLast) Color.White else KairoPrimary,
                        modifier = Modifier
                            .clickable { onNavigateToCrumb(index) }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
