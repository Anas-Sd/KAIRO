package com.kairo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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

import androidx.compose.material.icons.filled.Snooze

enum class SectionType {
    SNOOZED,
    OVERDUE,
    TODAY,
    TOMORROW,
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
    modifier: Modifier = Modifier,
    showCheckbox: Boolean = false,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null
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
            if (showCheckbox) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isChecked) KairoPrimary else Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (isChecked) KairoPrimary else Color.White.copy(alpha = 0.35f),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable { onCheckedChange?.invoke(!isChecked) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            when (sectionType) {
                SectionType.SNOOZED -> {
                    Icon(
                        imageVector = Icons.Default.Snooze,
                        contentDescription = "Snoozed",
                        tint = Color(0xFFFFB77D),
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                SectionType.TOMORROW -> {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Tomorrow",
                        tint = Color(0xFF818CF8),
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
                color = when (sectionType) {
                    SectionType.SNOOZED -> Color(0xFFFFB77D)
                    SectionType.OVERDUE -> KairoError
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            // Count badge
            val badgeBg = when (sectionType) {
                SectionType.SNOOZED -> Color(0xFFFFB77D).copy(alpha = 0.2f)
                SectionType.OVERDUE -> KairoErrorContainer
                else -> KairoSurfaceContainerHigh
            }
            val badgeColor = when (sectionType) {
                SectionType.SNOOZED -> Color(0xFFFFB77D)
                SectionType.OVERDUE -> KairoOnErrorContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

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
            tint = when (sectionType) {
                SectionType.SNOOZED -> Color(0xFFFFB77D).copy(alpha = 0.8f)
                SectionType.OVERDUE -> KairoError.copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation)
        )
    }
}
