package com.kairo.app.feature.lists.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh

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
