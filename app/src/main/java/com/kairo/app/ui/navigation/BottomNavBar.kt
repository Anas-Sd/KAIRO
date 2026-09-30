package com.kairo.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Tasks : Screen("tasks", "Tasks", Icons.Default.CheckCircle)
    object Lists : Screen("lists", "Lists", Icons.Default.FormatListBulleted)
    object Habits : Screen("habits", "Habits", Icons.Default.LocalFireDepartment)
    object Calendar : Screen("calendar", "Calendar", Icons.Default.CalendarToday)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.TrendingUp)
    object AiAssistant : Screen("ai_assistant", "AI", Icons.Default.CheckCircle)
}

val bottomNavItems = listOf(
    Screen.Tasks,
    Screen.Lists,
    Screen.Habits,
    Screen.Calendar,
    Screen.Analytics
)

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KairoBackground.copy(alpha = 0.95f))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            color = KairoOutlineVariant.copy(alpha = 0.15f),
            thickness = 1.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            bottomNavItems.forEach { screen ->
                val isSelected = currentRoute == screen.route

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToRoute(screen.route) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 28.dp)
                            .background(
                                color = if (isSelected) KairoPrimary.copy(alpha = 0.15f) else KairoBackground,
                                shape = RoundedCornerShape(100.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = if (isSelected) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = screen.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
