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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kairo.app.ui.theme.KairoPrimary
import java.util.UUID

data class TaskList(
    val id: String = UUID.randomUUID().toString(),
    val userCode: String = "DEFAULT",
    val name: String,
    val iconName: String = "list",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ListItem(
    val id: String = UUID.randomUUID().toString(),
    val userCode: String = "DEFAULT",
    val listId: String,
    val parentId: String? = null,
    val title: String,
    val iconName: String = "list",
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
    val progressPercentage: Int,
    val isCompleted: Boolean = (totalCount > 0 && completedCount == totalCount)
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

    val popularEmojis: List<String> = listOf(
        "📝", "🛒", "🎯", "🚀", "💡", "💼", "📚", "🏋️", "✈️", "🏠", "⭐", "📦", "💻", "❤️", "🔥", "✅", "🎉", "🎨", "🍔", "💰"
    )

    fun isEmoji(iconKey: String): Boolean {
        if (iconKey.startsWith("emoji:")) return true
        return iconKey.any { Character.isSurrogate(it) || Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
    }

    fun extractEmoji(iconKey: String): String {
        return if (iconKey.startsWith("emoji:")) iconKey.removePrefix("emoji:") else iconKey
    }

    fun getIcon(key: String): ImageVector {
        return icons.firstOrNull { it.key.equals(key, ignoreCase = true) }?.icon ?: Icons.AutoMirrored.Filled.FormatListBulleted
    }
}

@Composable
fun ListIconDisplay(
    iconKey: String,
    modifier: Modifier = Modifier,
    tint: Color = KairoPrimary,
    emojiSize: TextUnit = 18.sp
) {
    if (ListIconCatalog.isEmoji(iconKey)) {
        val emoji = ListIconCatalog.extractEmoji(iconKey)
        Text(
            text = emoji,
            fontSize = emojiSize,
            textAlign = TextAlign.Center
        )
    } else {
        Icon(
            imageVector = ListIconCatalog.getIcon(iconKey),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}
