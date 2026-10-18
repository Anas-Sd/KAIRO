package com.kairo.app.feature.lists.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kairo.app.KairoApplication
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.feature.lists.model.ListWithStats
import com.kairo.app.feature.lists.model.TaskList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ListRepository {

    private const val TAG = "ListRepository"
    private const val PREFS_NAME = "kairo_lists_prefs"
    private const val KEY_LISTS = "saved_task_lists"
    private const val KEY_ITEMS = "saved_list_items"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences? by lazy {
        if (KairoApplication.isInitialized) {
            KairoApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } else null
    }

    private val _lists = MutableStateFlow<List<TaskList>>(emptyList())
    val lists: StateFlow<List<TaskList>> = _lists.asStateFlow()

    private val _items = MutableStateFlow<List<ListItem>>(emptyList())
    val items: StateFlow<List<ListItem>> = _items.asStateFlow()

    private val _listsWithStats = MutableStateFlow<List<ListWithStats>>(emptyList())
    val listsWithStats: StateFlow<List<ListWithStats>> = _listsWithStats.asStateFlow()

    init {
        if (KairoApplication.isInitialized) {
            loadFromDisk()
        }
    }

    private fun loadFromDisk() {
        try {
            val listsJson = prefs?.getString(KEY_LISTS, null)
            val itemsJson = prefs?.getString(KEY_ITEMS, null)

            val loadedLists = mutableListOf<TaskList>()
            if (!listsJson.isNullOrBlank()) {
                val array = JSONArray(listsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedLists.add(
                        TaskList(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            iconName = obj.optString("icon_name", "list"),
                            position = obj.optInt("position", i),
                            createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                        )
                    )
                }
            }

            val loadedItems = mutableListOf<ListItem>()
            if (!itemsJson.isNullOrBlank()) {
                val array = JSONArray(itemsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedItems.add(
                        ListItem(
                            id = obj.getString("id"),
                            listId = obj.getString("list_id"),
                            parentId = if (obj.has("parent_id") && !obj.isNull("parent_id")) obj.getString("parent_id") else null,
                            title = obj.getString("title"),
                            notes = if (obj.has("notes") && !obj.isNull("notes")) obj.getString("notes") else null,
                            targetDate = if (obj.has("target_date") && !obj.isNull("target_date")) obj.getString("target_date") else null,
                            targetDateMillis = if (obj.has("target_date_millis") && !obj.isNull("target_date_millis")) obj.getLong("target_date_millis") else null,
                            isCompleted = obj.optBoolean("is_completed", false),
                            position = obj.optInt("position", i),
                            completedAt = if (obj.has("completed_at") && !obj.isNull("completed_at")) obj.getLong("completed_at") else null,
                            createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                        )
                    )
                }
            }

            _lists.value = loadedLists.sortedBy { it.position }
            _items.value = loadedItems.sortedBy { it.position }
            recalculateStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading lists from disk: ${e.message}", e)
        }
    }

    private fun saveToDisk() {
        scope.launch {
            try {
                val currentLists = _lists.value
                val listsArray = JSONArray()
                currentLists.forEach { list ->
                    listsArray.put(
                        JSONObject().apply {
                            put("id", list.id)
                            put("name", list.name)
                            put("icon_name", list.iconName)
                            put("position", list.position)
                            put("created_at", list.createdAt)
                            put("updated_at", list.updatedAt)
                        }
                    )
                }

                val currentItems = _items.value
                val itemsArray = JSONArray()
                currentItems.forEach { item ->
                    itemsArray.put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("list_id", item.listId)
                            put("parent_id", item.parentId)
                            put("title", item.title)
                            put("notes", item.notes)
                            put("target_date", item.targetDate)
                            put("target_date_millis", item.targetDateMillis)
                            put("is_completed", item.isCompleted)
                            put("position", item.position)
                            put("completed_at", item.completedAt)
                            put("created_at", item.createdAt)
                            put("updated_at", item.updatedAt)
                        }
                    )
                }

                prefs?.edit()
                    ?.putString(KEY_LISTS, listsArray.toString())
                    ?.putString(KEY_ITEMS, itemsArray.toString())
                    ?.apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving lists to disk: ${e.message}", e)
            }
        }
    }

    private fun recalculateStats() {
        val currentLists = _lists.value
        val currentItems = _items.value

        val stats = currentLists.map { list ->
            val listItems = currentItems.filter { it.listId == list.id }
            val total = listItems.size
            val completed = listItems.count { it.isCompleted }
            val active = total - completed
            val percentage = if (total > 0) ((completed.toDouble() / total.toDouble()) * 100).toInt() else 0

            ListWithStats(
                list = list,
                totalCount = total,
                completedCount = completed,
                activeCount = active,
                progressPercentage = percentage
            )
        }

        _listsWithStats.value = stats
    }

    // ==========================================
    // LIST OPERATIONS
    // ==========================================

    fun createList(name: String, iconName: String, initialSubtasks: List<String> = emptyList()): TaskList {
        val cleanName = name.trim()
        val current = _lists.value
        val newList = TaskList(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            iconName = iconName,
            position = current.size,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val updatedLists = current + newList
        _lists.value = updatedLists

        // If initial subtasks were entered, add them directly
        if (initialSubtasks.isNotEmpty()) {
            val newItems = initialSubtasks
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapIndexed { index, subtaskTitle ->
                    ListItem(
                        id = UUID.randomUUID().toString(),
                        listId = newList.id,
                        parentId = null,
                        title = subtaskTitle,
                        position = index,
                        createdAt = System.currentTimeMillis() + index,
                        updatedAt = System.currentTimeMillis() + index
                    )
                }
            _items.value = _items.value + newItems
        }

        recalculateStats()
        saveToDisk()
        return newList
    }

    fun updateList(id: String, name: String, iconName: String) {
        val cleanName = name.trim()
        val updated = _lists.value.map {
            if (it.id == id) {
                it.copy(name = cleanName, iconName = iconName, updatedAt = System.currentTimeMillis())
            } else it
        }
        _lists.value = updated
        recalculateStats()
        saveToDisk()
    }

    fun deleteList(id: String) {
        _lists.value = _lists.value.filter { it.id != id }
        _items.value = _items.value.filter { it.listId != id }
        recalculateStats()
        saveToDisk()
    }

    fun reorderLists(newOrder: List<TaskList>) {
        val updated = newOrder.mapIndexed { index, list -> list.copy(position = index) }
        _lists.value = updated
        recalculateStats()
        saveToDisk()
    }

    // ==========================================
    // ITEM OPERATIONS
    // ==========================================

    fun addListItem(
        listId: String,
        title: String,
        notes: String? = null,
        targetDate: String? = null,
        targetDateMillis: Long? = null,
        parentId: String? = null
    ): ListItem {
        val cleanTitle = title.trim()
        val listItems = _items.value.filter { it.listId == listId }
        val newItem = ListItem(
            id = UUID.randomUUID().toString(),
            listId = listId,
            parentId = parentId,
            title = cleanTitle,
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
            targetDate = targetDate,
            targetDateMillis = targetDateMillis,
            isCompleted = false,
            position = listItems.size,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        _items.value = _items.value + newItem
        recalculateStats()
        saveToDisk()
        return newItem
    }

    fun addMultipleListItems(
        listId: String,
        titles: List<String>,
        parentId: String? = null
    ) {
        val existingItems = _items.value.filter { it.listId == listId }
        var currentPos = existingItems.size
        val newItems = mutableListOf<ListItem>()

        titles.map { it.trim() }.filter { it.isNotBlank() }.forEach { title ->
            newItems.add(
                ListItem(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    parentId = parentId,
                    title = title,
                    position = currentPos++,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        if (newItems.isNotEmpty()) {
            _items.value = _items.value + newItems
            recalculateStats()
            saveToDisk()
        }
    }

    fun updateListItem(item: ListItem) {
        val updated = _items.value.map {
            if (it.id == item.id) {
                item.copy(updatedAt = System.currentTimeMillis())
            } else it
        }
        _items.value = updated
        recalculateStats()
        saveToDisk()
    }

    fun toggleItemCompleted(itemId: String) {
        val target = _items.value.find { it.id == itemId } ?: return
        val newCompleted = !target.isCompleted
        val completedAt = if (newCompleted) System.currentTimeMillis() else null

        val updated = _items.value.map {
            if (it.id == itemId) {
                it.copy(
                    isCompleted = newCompleted,
                    completedAt = completedAt,
                    updatedAt = System.currentTimeMillis()
                )
            } else it
        }

        _items.value = updated
        recalculateStats()
        saveToDisk()
    }

    fun checkAllItems(listId: String, completed: Boolean) {
        val timestamp = if (completed) System.currentTimeMillis() else null
        val updated = _items.value.map {
            if (it.listId == listId) {
                it.copy(isCompleted = completed, completedAt = timestamp, updatedAt = System.currentTimeMillis())
            } else it
        }
        _items.value = updated
        recalculateStats()
        saveToDisk()
    }

    fun deleteListItem(itemId: String, deleteSubtasks: Boolean) {
        val allCurrent = _items.value
        val target = allCurrent.find { it.id == itemId } ?: return

        if (deleteSubtasks) {
            val descendantIds = getDescendantIds(itemId, allCurrent)
            val idsToDelete = descendantIds + itemId
            _items.value = allCurrent.filter { !idsToDelete.contains(it.id) }
        } else {
            // Reparent direct children to target's parent
            val updated = allCurrent.map {
                if (it.parentId == itemId) {
                    it.copy(parentId = target.parentId, updatedAt = System.currentTimeMillis())
                } else it
            }.filter { it.id != itemId }
            _items.value = updated
        }

        recalculateStats()
        saveToDisk()
    }

    fun adjustItemParent(itemId: String, newParentId: String?, moveSubtasks: Boolean) {
        val allCurrent = _items.value
        val target = allCurrent.find { it.id == itemId } ?: return

        // Prevent cycle: newParentId cannot be in target's descendants
        if (newParentId != null) {
            val descendants = getDescendantIds(itemId, allCurrent)
            if (descendants.contains(newParentId) || newParentId == itemId) {
                Log.w(TAG, "Cannot reparent item to its own descendant")
                return
            }
        }

        if (moveSubtasks) {
            // Move target under newParentId; children remain attached to target
            val updated = allCurrent.map {
                if (it.id == itemId) {
                    it.copy(parentId = newParentId, updatedAt = System.currentTimeMillis())
                } else it
            }
            _items.value = updated
        } else {
            // Leave subtasks attached to target's original parent, move only target
            val originalParent = target.parentId
            val updated = allCurrent.map {
                when (it.id) {
                    itemId -> it.copy(parentId = newParentId, updatedAt = System.currentTimeMillis())
                    else -> {
                        if (it.parentId == itemId) {
                            it.copy(parentId = originalParent, updatedAt = System.currentTimeMillis())
                        } else it
                    }
                }
            }
            _items.value = updated
        }

        recalculateStats()
        saveToDisk()
    }

    fun getDescendantIds(itemId: String, allItems: List<ListItem> = _items.value): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(itemId)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val children = allItems.filter { it.parentId == curr }
            for (c in children) {
                if (c.id != itemId && result.add(c.id)) {
                    queue.add(c.id)
                }
            }
        }
        return result
    }

    fun getAncestorIds(itemId: String, allItems: List<ListItem> = _items.value): List<String> {
        val byId = allItems.associateBy { it.id }
        val ancestors = mutableListOf<String>()
        var curr = byId[itemId]?.parentId
        val visited = mutableSetOf(itemId)
        while (curr != null && visited.add(curr)) {
            ancestors.add(curr)
            curr = byId[curr]?.parentId
        }
        return ancestors
    }

    fun reorderItems(listId: String, newOrder: List<ListItem>) {
        val updatedMap = newOrder.mapIndexed { index, item -> item.id to index }.toMap()
        val updated = _items.value.map { item ->
            if (item.listId == listId && updatedMap.containsKey(item.id)) {
                item.copy(position = updatedMap[item.id]!!)
            } else item
        }
        _items.value = updated
        saveToDisk()
    }
}
