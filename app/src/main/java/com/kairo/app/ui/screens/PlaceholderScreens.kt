package com.kairo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
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
import com.kairo.app.ui.components.AppHeader
import com.kairo.app.ui.components.SettingsDialog
import com.kairo.app.ui.theme.KairoBackground

@Composable
fun ListsScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
            .statusBarsPadding()
    ) {
        AppHeader(
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onSearchQueryChange = { searchQuery = it },
            onToggleSearch = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            },
            onCloseSearch = {
                isSearchActive = false
                searchQuery = ""
            },
            searchPlaceholder = "Search lists...",
            onSettingsClick = { showSettingsDialog = true }
        )

        PlaceholderContent(
            title = "Lists",
            subtitle = if (searchQuery.isNotBlank()) "Searching lists for \"$searchQuery\"..." else "Organize tasks into flexible projects, shopping, and to-do lists."
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
    }
}

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
            .statusBarsPadding()
    ) {
        AppHeader(
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onSearchQueryChange = { searchQuery = it },
            onToggleSearch = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            },
            onCloseSearch = {
                isSearchActive = false
                searchQuery = ""
            },
            searchPlaceholder = "Search events...",
            onSettingsClick = { showSettingsDialog = true }
        )

        PlaceholderContent(
            title = "Calendar",
            subtitle = if (searchQuery.isNotBlank()) "Searching calendar events for \"$searchQuery\"..." else "Unified monthly & weekly timeline view of all scheduled tasks."
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
    }
}

@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
            .statusBarsPadding()
    ) {
        AppHeader(
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onSearchQueryChange = { searchQuery = it },
            onToggleSearch = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            },
            onCloseSearch = {
                isSearchActive = false
                searchQuery = ""
            },
            searchPlaceholder = "Search insights...",
            onSettingsClick = { showSettingsDialog = true }
        )

        PlaceholderContent(
            title = "Analytics & Insights",
            subtitle = if (searchQuery.isNotBlank()) "Searching analytics for \"$searchQuery\"..." else "Weekly productivity scores, completion rates, and focus trends."
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
    }
}

@Composable
private fun PlaceholderContent(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}
