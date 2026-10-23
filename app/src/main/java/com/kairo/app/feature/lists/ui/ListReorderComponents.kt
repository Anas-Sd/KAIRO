package com.kairo.app.feature.lists.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.theme.KairoOutline
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ListCardLeadingControl:
 * Vertical column on the left of list/item cards:
 * - 6 dots DragIndicator handle on top (touch target for dragging to reorder)
 * - 21.dp Squircle Checkbox below it (for toggling completion with bounce animation)
 */
@Composable
fun ListCardLeadingControl(
    isCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    isBeingDragged: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isLocallyCompleted by remember(isCompleted) { mutableStateOf(isCompleted) }
    val bounceScale = remember { Animatable(1f) }

    fun handleToggle() {
        val next = !isLocallyCompleted
        isLocallyCompleted = next
        coroutineScope.launch {
            bounceScale.animateTo(
                targetValue = 1.35f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
            )
            bounceScale.animateTo(
                targetValue = 0.88f,
                animationSpec = tween(durationMillis = 80, easing = LinearOutSlowInEasing)
            )
            bounceScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
            )
            delay(280)
            onToggleCompleted()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(end = 10.dp)
    ) {
        // Drag Handle (6 dots: DragIndicator) - only shown when not in selection mode
        if (!isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = if (isBeingDragged) KairoPrimary else KairoOutline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) KairoPrimary else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (isSelected) KairoPrimary else Color.White.copy(alpha = 0.35f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectionChange?.invoke(!isSelected) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            // Squircle Checkbox (21.dp with 6.dp rounded corners matching TaskCard)
            val checkboxBg by animateColorAsState(
                targetValue = if (isLocallyCompleted) Color(0xFF10B981) else Color.Transparent,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "listCheckboxBg"
            )

            Box(
                modifier = Modifier
                    .size(21.dp)
                    .scale(bounceScale.value)
                    .clip(RoundedCornerShape(6.dp))
                    .background(checkboxBg, shape = RoundedCornerShape(6.dp))
                    .border(
                        1.5.dp,
                        if (isLocallyCompleted) Color.Transparent else Color.White.copy(alpha = 0.45f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { handleToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isLocallyCompleted) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ListReorderHandle(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: (() -> Unit)? = null,
    onMoveToBottom: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(KairoSurfaceContainerHigh)
        ) {
            DropdownMenuItem(
                text = { Text("Move Up") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (canMoveUp) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                },
                enabled = canMoveUp,
                onClick = {
                    expanded = false
                    onMoveUp()
                }
            )

            DropdownMenuItem(
                text = { Text("Move Down") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (canMoveDown) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                },
                enabled = canMoveDown,
                onClick = {
                    expanded = false
                    onMoveDown()
                }
            )

            if (onMoveToTop != null) {
                DropdownMenuItem(
                    text = { Text("Move to Top") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignTop,
                            contentDescription = null,
                            tint = if (canMoveUp) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    },
                    enabled = canMoveUp,
                    onClick = {
                        expanded = false
                        onMoveToTop()
                    }
                )
            }

            if (onMoveToBottom != null) {
                DropdownMenuItem(
                    text = { Text("Move to Bottom") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = null,
                            tint = if (canMoveDown) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    },
                    enabled = canMoveDown,
                    onClick = {
                        expanded = false
                        onMoveToBottom()
                    }
                )
            }
        }
    }
}
