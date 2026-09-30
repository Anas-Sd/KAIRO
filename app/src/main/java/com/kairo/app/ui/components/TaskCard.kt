package com.kairo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.ContextType
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoErrorContainer
import com.kairo.app.ui.theme.KairoOnErrorContainer
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    onToggleCompletion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOverdue = task.section == TaskSection.OVERDUE && !task.isCompleted
    val borderColor = if (isOverdue) {
        KairoError.copy(alpha = 0.35f)
    } else {
        KairoOutlineVariant.copy(alpha = 0.2f)
    }

    val checkboxBg by animateColorAsState(
        targetValue = if (task.isCompleted) KairoPrimaryContainer else KairoSurfaceContainerHighest,
        label = "checkboxBg"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "checkScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleCompletion() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = KairoSurfaceContainerLowest
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox squircle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .scale(checkScale)
                    .background(checkboxBg, shape = RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (task.isCompleted) Color.Transparent else KairoOutlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleCompletion() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Task content
            Column(modifier = Modifier.weight(1f)) {
                // Tags row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Priority tag if Urgent
                    if (task.priority == Priority.URGENT) {
                        TagBadge(
                            text = "Urgent",
                            containerColor = KairoErrorContainer,
                            contentColor = KairoOnErrorContainer
                        )
                    }

                    // Category tag
                    val categoryColor = when (task.category.label) {
                        "Work" -> KairoPrimary
                        "Habits" -> KairoSecondary
                        "Finance" -> KairoSecondary
                        else -> KairoPrimary
                    }
                    TagBadge(
                        text = task.category.label,
                        containerColor = categoryColor.copy(alpha = 0.15f),
                        contentColor = categoryColor
                    )

                    // Secondary tag if any
                    task.secondaryTag?.let { tag ->
                        TagBadge(
                            text = tag,
                            containerColor = KairoSurfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Task Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Sub-metadata (Time, Context)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Due time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val icon = if (isOverdue) Icons.Default.Warning else Icons.Default.Schedule
                        val tint = if (isOverdue) KairoError else KairoPrimary

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = task.dueDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) KairoError else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Context indicator (Meet / Room / File)
                    task.contextLabel?.let { label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val contextIcon = when (task.contextType) {
                                ContextType.MEETING -> Icons.Default.Videocam
                                ContextType.LOCATION -> Icons.Default.Place
                                ContextType.ATTACHMENT -> Icons.Default.AttachFile
                                ContextType.NONE -> null
                            }
                            contextIcon?.let {
                                Icon(
                                    imageVector = it,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(containerColor, shape = RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp
        )
    }
}
