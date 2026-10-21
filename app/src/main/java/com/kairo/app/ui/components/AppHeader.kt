package com.kairo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.sync.SyncManager
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppHeader(
    searchQuery: String = "",
    isSearchActive: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    searchPlaceholder: String = "Search...",
    onSettingsClick: () -> Unit,
    moreMenuContent: (@Composable (dismissMenu: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val session by AuthManager.sessionState.collectAsState()
    val isOnline by SyncManager.isOnline.collectAsState()
    val isSyncing by SyncManager.isSyncing.collectAsState()
    val pendingSyncCount by SyncManager.pendingSyncCount.collectAsState()
    val displayName = session?.name ?: "Anas"
    val currentDateStr = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    val coroutineScope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KairoBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Display Name + Cloud Sync Pill + Date
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Cloud Sync Pill
                    val pillBg = when {
                        isSyncing -> KairoPrimary.copy(alpha = 0.15f)
                        !isOnline -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                        else -> KairoSecondary.copy(alpha = 0.12f)
                    }
                    val pillBorder = when {
                        isSyncing -> KairoPrimary.copy(alpha = 0.4f)
                        !isOnline -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                        else -> KairoSecondary.copy(alpha = 0.35f)
                    }
                    val pillTint = when {
                        isSyncing -> KairoPrimary
                        !isOnline -> Color(0xFFF59E0B)
                        else -> KairoSecondary
                    }
                    val pillIcon = when {
                        isSyncing -> Icons.Default.Sync
                        !isOnline -> Icons.Default.CloudOff
                        else -> Icons.Default.CloudDone
                    }
                    val pillText = when {
                        isSyncing -> "Syncing..."
                        !isOnline -> if (pendingSyncCount > 0) "Offline ($pendingSyncCount)" else "Offline"
                        pendingSyncCount > 0 -> "Syncing ($pendingSyncCount)"
                        else -> "Synced"
                    }

                    Box(
                        modifier = Modifier
                            .background(color = pillBg, shape = RoundedCornerShape(100.dp))
                            .border(1.dp, pillBorder, RoundedCornerShape(100.dp))
                            .clickable { onSettingsClick() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = pillIcon,
                                contentDescription = pillText,
                                tint = pillTint,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = pillText,
                                color = pillTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = currentDateStr,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right: 1. Search -> 2. Settings -> 3. 3-dots Menu (Overall Refresh / Actions)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Search button
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. Settings button
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 3. 3-dots Menu button (Positioned on the far right of Settings)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Global Overall Refresh (syncs tasks, habits, and cloud)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Refresh",
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = KairoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    SyncManager.performSync(forceCheckNetwork = true)
                                    HabitRepository.fetchFromSupabase()
                                    ListRepository.fetchFromSupabase()
                                }
                            }
                        )

                        // Additional menu items if passed (e.g. Select Mode)
                        moreMenuContent?.invoke { showMenu = false }
                    }
                }
            }
        }

        // Expandable Search Bar
        AnimatedVisibility(visible = isSearchActive) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = searchPlaceholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            } else {
                                onCloseSearch()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}
