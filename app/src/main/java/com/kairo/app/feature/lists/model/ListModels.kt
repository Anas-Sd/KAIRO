package com.kairo.app.feature.lists.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

data class TaskList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconName: String = "list",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ListItem(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val parentId: String? = null,
    val title: String,
    val notes: String? = null,
    val targetDate: String? = null,
    val targetDateMillis: Long? = null,
    val isCompleted: Boolean = false,
    val position: Int = 0,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ListWithStats(
    val list: TaskList,
    val totalCount: Int,
    val completedCount: Int,
    val activeCount: Int,
    val progressPercentage: Int
)

data class ListIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

object ListIconCatalog {
    val icons: List<ListIconOption> = listOf(
        ListIconOption("list", "General", Icons.AutoMirrored.Filled.FormatListBulleted),
        ListIconOption("checklist", "Checklist", Icons.Default.Checklist),
        ListIconOption("shopping", "Shopping", Icons.Default.ShoppingCart),
        ListIconOption("work", "Work", Icons.Default.Work),
        ListIconOption("study", "Study", Icons.AutoMirrored.Filled.MenuBook),
        ListIconOption("fitness", "Fitness", Icons.Default.FitnessCenter),
        ListIconOption("travel", "Travel", Icons.Default.Flight),
        ListIconOption("ideas", "Ideas", Icons.Default.Lightbulb),
        ListIconOption("home", "Home", Icons.Default.Home),
        ListIconOption("star", "Goals", Icons.Default.Star),
        ListIconOption("packing", "Packing", Icons.Default.Inventory2),
        ListIconOption("code", "Code", Icons.Default.Code),
        ListIconOption("favorite", "Wishlist", Icons.Default.Favorite)
    )

    fun getIcon(key: String): ImageVector {
        return icons.firstOrNull { it.key.equals(key, ignoreCase = true) }?.icon ?: Icons.AutoMirrored.Filled.FormatListBulleted
    }
}
