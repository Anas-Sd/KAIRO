package com.kairo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoPrimary

@Composable
fun ListsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Lists",
        subtitle = "Organize tasks into flexible projects, shopping, and to-do lists.",
        modifier = modifier
    )
}

@Composable
fun HabitsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Habits Tracker",
        subtitle = "Build daily streaks, monitor routines, and hit consistency targets.",
        modifier = modifier
    )
}

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Calendar",
        subtitle = "Unified monthly & weekly timeline view of all scheduled tasks.",
        modifier = modifier
    )
}

@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Analytics & Insights",
        subtitle = "Weekly productivity scores, completion rates, and focus trends.",
        modifier = modifier
    )
}

@Composable
fun AiAssistantScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.kairo.app.feature.ai.AiFeatureFacade.AiAssistantView(
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
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
