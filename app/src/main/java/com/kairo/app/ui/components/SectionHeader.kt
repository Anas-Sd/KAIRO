package com.kairo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoErrorContainer
import com.kairo.app.ui.theme.KairoOnErrorContainer
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh

enum class SectionType {
    OVERDUE,
    TODAY,
    UPCOMING,
    COMPLETED
}

@Composable
fun SectionHeader(
    title: String,
    countText: String,
    sectionType: SectionType,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "chevronRotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (sectionType) {
                SectionType.OVERDUE -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Overdue",
                        tint = KairoError,
                        modifier = Modifier.size(18.dp)
                    )
                }
                SectionType.TODAY -> {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "Today",
                        tint = KairoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                SectionType.UPCOMING -> {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "Upcoming",
                        tint = Color(0xFF67E8F9),
                        modifier = Modifier.size(18.dp)
                    )
                }
                SectionType.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = KairoSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (sectionType == SectionType.OVERDUE) KairoError else MaterialTheme.colorScheme.onSurface
            )

            // Count badge
            val badgeBg = if (sectionType == SectionType.OVERDUE) KairoErrorContainer else KairoSurfaceContainerHigh
            val badgeColor = if (sectionType == SectionType.OVERDUE) KairoOnErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(100.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = countText,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = if (sectionType == SectionType.OVERDUE) KairoError.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation)
        )
    }
}
