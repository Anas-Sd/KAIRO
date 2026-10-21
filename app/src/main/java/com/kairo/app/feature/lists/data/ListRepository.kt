package com.kairo.app.feature.lists.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kairo.app.KairoApplication
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.feature.lists.model.ListWithStats
import com.kairo.app.feature.lists.model.TaskList
import com.kairo.app.feature.sync.SupabaseClient
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
            fetchFromSupabase()
        }
    }

    private fun getUserCodeSafe(): String {
        return runCatching { AuthManager.getUserCode() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "DEFAULT"
    }

    private fun TaskList.toDto(): TaskListDto = TaskListDto(
        id = id,
        userCode = if (userCode.isNotBlank() && userCode != "DEFAULT") userCode else getUserCodeSafe(),
        name = name,
        iconName = iconName,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ListItem.toDto(): ListItemDto = ListItemDto(
        id = id,
        userCode = if (userCode.isNotBlank() && userCode != "DEFAULT") userCode else getUserCodeSafe(),
        listId = listId,
        parentId = parentId,
        title = title,
        iconName = iconName,
        notes = notes,
        targetDate = targetDate,
        targetDateMillis = targetDateMillis,
        isCompleted = isCompleted,
        position = position,
        completedAt = completedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun fetchFromSupabase() {
        scope.launch {
            val userCode = getUserCodeSafe()
            val listsResult = SupabaseClient.getTaskLists(userCode)
            val itemsResult = SupabaseClient.getListItems(userCode)

            var hasChanges = false
            if (listsResult.isSuccess) {
                val dtoList = listsResult.getOrNull().orEmpty()
                val remoteLists = dtoList.map { dto ->
                    TaskList(
                        id = dto.id,
                        userCode = dto.userCode,
                        name = dto.name,
                        iconName = dto.iconName,
                        position = dto.position,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt
                    )
                }.sortedBy { it.position }

                if (remoteLists.isNotEmpty()) {
                    _lists.value = remoteLists
                    hasChanges = true
                } else if (_lists.value.isNotEmpty()) {
                    _lists.value.forEach { SupabaseClient.upsertTaskList(it.toDto()) }
                }
            } else {
                Log.w(TAG, "Could not fetch lists from Supabase: ${listsResult.exceptionOrNull()?.message}")
            }

            if (itemsResult.isSuccess) {
                val dtoList = itemsResult.getOrNull().orEmpty()
                val remoteItems = dtoList.map { dto ->
                    ListItem(
                        id = dto.id,
                        userCode = dto.userCode,
                        listId = dto.listId,
                        parentId = dto.parentId,
                        title = dto.title,
                        iconName = dto.iconName,
                        notes = dto.notes,
                        targetDate = dto.targetDate,
                        targetDateMillis = dto.targetDateMillis,
                        isCompleted = dto.isCompleted,
                        position = dto.position,
                        completedAt = dto.completedAt,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt
                    )
                }.sortedBy { it.position }

                if (remoteItems.isNotEmpty()) {
                    _items.value = remoteItems
                    hasChanges = true
                } else if (_items.value.isNotEmpty()) {
                    SupabaseClient.upsertListItems(_items.value.map { it.toDto() })
                }
            } else {
                Log.w(TAG, "Could not fetch list items from Supabase: ${itemsResult.exceptionOrNull()?.message}")
            }

            if (hasChanges) {
                recalculateStats()
                saveToDisk()
            }
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
                            iconName = obj.optString("icon_name", "list"),
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
                            put("icon_name", item.iconName)
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
            // Count root items under the list (parentId == null), not deeply nested subtasks
            val rootItems = listItems.filter { it.parentId == null }
            val total = rootItems.size
            val completed = rootItems.count { it.isCompleted }
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
        val userCode = getUserCodeSafe()
        val newList = TaskList(
            id = UUID.randomUUID().toString(),
            userCode = userCode,
            name = cleanName,
            iconName = iconName,
            position = current.size,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val updatedLists = current + newList
        _lists.value = updatedLists

        var newItemsList: List<ListItem> = emptyList()
        // If initial subtasks were entered, add them directly
        if (initialSubtasks.isNotEmpty()) {
            newItemsList = initialSubtasks
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapIndexed { index, subtaskTitle ->
                    ListItem(
                        id = UUID.randomUUID().toString(),
                        userCode = userCode,
                        listId = newList.id,
                        parentId = null,
                        title = subtaskTitle,
                        position = index,
                        createdAt = System.currentTimeMillis() + index,
                        updatedAt = System.currentTimeMillis() + index
                    )
                }
            _items.value = _items.value + newItemsList
        }

        recalculateStats()
        saveToDisk()

        // Sync to Supabase
        scope.launch {
            SupabaseClient.upsertTaskList(newList.toDto())
            if (newItemsList.isNotEmpty()) {
                SupabaseClient.upsertListItems(newItemsList.map { it.toDto() })
            }
        }

        return newList
    }

    fun updateList(id: String, name: String, iconName: String) {
        val cleanName = name.trim()
        var updatedList: TaskList? = null
        val updated = _lists.value.map {
            if (it.id == id) {
                val list = it.copy(name = cleanName, iconName = iconName, updatedAt = System.currentTimeMillis())
                updatedList = list
                list
            } else it
        }
        _lists.value = updated
        recalculateStats()
        saveToDisk()

        scope.launch {
            updatedList?.let { SupabaseClient.upsertTaskList(it.toDto()) }
        }
    }

    fun deleteList(id: String) {
        _lists.value = _lists.value.filter { it.id != id }
        _items.value = _items.value.filter { it.listId != id }
        recalculateStats()
        saveToDisk()

        scope.launch {
            SupabaseClient.deleteTaskList(id)
        }
    }

    fun reorderLists(newOrder: List<TaskList>) {
        val updated = newOrder.mapIndexed { index, list -> list.copy(position = index) }
        _lists.value = updated
        recalculateStats()
        saveToDisk()

        scope.launch {
            updated.forEach { SupabaseClient.upsertTaskList(it.toDto()) }
        }
    }

    fun moveListUp(listId: String) {
        val current = _lists.value.sortedBy { it.position }.toMutableList()
        val index = current.indexOfFirst { it.id == listId }
        if (index > 0) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            reorderLists(current)
        }
    }

    fun moveListDown(listId: String) {
        val current = _lists.value.sortedBy { it.position }.toMutableList()
        val index = current.indexOfFirst { it.id == listId }
        if (index != -1 && index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            reorderLists(current)
        }
    }

    fun moveListToTop(listId: String) {
        val current = _lists.value.sortedBy { it.position }.toMutableList()
        val index = current.indexOfFirst { it.id == listId }
        if (index > 0) {
            val item = current.removeAt(index)
            current.add(0, item)
            reorderLists(current)
        }
    }

    fun moveListToBottom(listId: String) {
        val current = _lists.value.sortedBy { it.position }.toMutableList()
        val index = current.indexOfFirst { it.id == listId }
        if (index != -1 && index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(item)
            reorderLists(current)
        }
    }

    fun moveListIntoList(sourceListId: String, targetListId: String) {
        if (sourceListId == targetListId) return
        val currentLists = _lists.value
        val sourceList = currentLists.find { it.id == sourceListId } ?: return
        val targetList = currentLists.find { it.id == targetListId } ?: return

        val currentItems = _items.value
        val sourceItems = currentItems.filter { it.listId == sourceListId }
        val targetItems = currentItems.filter { it.listId == targetListId }

        // 1. Create a parent item in targetList representing sourceList
        val parentItem = ListItem(
            id = UUID.randomUUID().toString(),
            listId = targetListId,
            parentId = null,
            title = sourceList.name,
            iconName = sourceList.iconName,
            position = targetItems.size,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // 2. Transfer all source items into targetList:
        // Root items of sourceList now have parentId = parentItem.id
        // Sub-items of sourceList keep their existing parentId within sourceList
        val migratedItems = sourceItems.map { item ->
            val newParentId = if (item.parentId == null) parentItem.id else item.parentId
            item.copy(
                listId = targetListId,
                parentId = newParentId,
                updatedAt = System.currentTimeMillis()
            )
        }

        // 3. Remove sourceList from _lists
        _lists.value = currentLists.filter { it.id != sourceListId }

        // 4. Update _items: remove old source items, add parentItem and migratedItems
        val remainingItems = currentItems.filter { it.listId != sourceListId }
        _items.value = remainingItems + parentItem + migratedItems

        recalculateStats()
        saveToDisk()

        scope.launch {
            SupabaseClient.deleteTaskList(sourceListId)
            SupabaseClient.upsertListItem(parentItem.toDto())
            SupabaseClient.upsertListItems(migratedItems.map { it.toDto() })
        }
    }

    // ==========================================
    // ITEM OPERATIONS
    // ==========================================

    fun addListItem(
        listId: String,
        title: String,
        iconName: String = "list",
        notes: String? = null,
        targetDate: String? = null,
        targetDateMillis: Long? = null,
        parentId: String? = null
    ): ListItem {
        val cleanTitle = title.trim()
        val listItems = _items.value.filter { it.listId == listId }
        val userCode = getUserCodeSafe()
        val newItem = ListItem(
            id = UUID.randomUUID().toString(),
            userCode = userCode,
            listId = listId,
            parentId = parentId,
            title = cleanTitle,
            iconName = iconName,
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

        scope.launch {
            SupabaseClient.upsertListItem(newItem.toDto())
        }

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
        val userCode = getUserCodeSafe()

        titles.map { it.trim() }.filter { it.isNotBlank() }.forEach { title ->
            newItems.add(
                ListItem(
                    id = UUID.randomUUID().toString(),
                    userCode = userCode,
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

            scope.launch {
                SupabaseClient.upsertListItems(newItems.map { it.toDto() })
            }
        }
    }

    fun convertLeafToParentWithSubtasks(
        itemId: String,
        updatedTitle: String,
        updatedIconName: String,
        subtaskTitles: List<String>
    ) {
        val all = _items.value.toMutableList()
        val index = all.indexOfFirst { it.id == itemId }
        if (index == -1) return
        val current = all[index]

        // 1. Update the item itself with new title and chosen icon
        val updatedItem = current.copy(
            title = updatedTitle.trim(),
            iconName = updatedIconName,
            updatedAt = System.currentTimeMillis()
        )
        all[index] = updatedItem

        // 2. Add subtask items
        val listId = current.listId
        val existingItems = all.filter { it.listId == listId }
        var currentPos = existingItems.size
        val userCode = getUserCodeSafe()
        val newSubtasks = mutableListOf<ListItem>()

        subtaskTitles.map { it.trim() }.filter { it.isNotBlank() }.forEach { title ->
            newSubtasks.add(
                ListItem(
                    id = UUID.randomUUID().toString(),
                    userCode = userCode,
                    listId = listId,
                    parentId = current.id,
                    title = title,
                    position = currentPos++,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        all.addAll(newSubtasks)
        _items.value = all
        recalculateStats()
        saveToDisk()

        scope.launch {
            val dtoList = listOf(updatedItem.toDto()) + newSubtasks.map { it.toDto() }
            SupabaseClient.upsertListItems(dtoList)
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

        scope.launch {
            SupabaseClient.upsertListItem(item.toDto())
        }
    }

    fun toggleItemCompleted(itemId: String) {
        val allItems = _items.value
        val target = allItems.find { it.id == itemId } ?: return
        val listId = target.listId
        val itemsInList = allItems.filter { it.listId == listId }
        val now = System.currentTimeMillis()
        val affectedIds = mutableSetOf<String>()

        if (!target.isCompleted) {
            // 1. Target and all recursive descendants become COMPLETED
            val toComplete = mutableSetOf(itemId)
            toComplete.addAll(getDescendantIds(itemId, itemsInList))

            // 2. Cascade upward: A parent becomes completed if ALL its direct children are completed
            val byId = itemsInList.associateBy { it.id }.toMutableMap()
            for (id in toComplete) {
                byId[id]?.let {
                    byId[id] = it.copy(isCompleted = true, completedAt = now, updatedAt = now)
                }
            }

            var currentParentId = target.parentId
            val visited = mutableSetOf<String>()
            while (currentParentId != null && visited.add(currentParentId)) {
                val parentItem = byId[currentParentId] ?: break
                val siblings = byId.values.filter { it.parentId == currentParentId }
                val allSiblingsDone = siblings.isNotEmpty() && siblings.all { it.isCompleted || toComplete.contains(it.id) }
                if (allSiblingsDone) {
                    toComplete.add(currentParentId)
                    byId[currentParentId] = parentItem.copy(
                        isCompleted = true,
                        completedAt = now,
                        updatedAt = now
                    )
                    currentParentId = parentItem.parentId
                } else {
                    break
                }
            }

            affectedIds.addAll(toComplete)
            _items.value = allItems.map {
                if (toComplete.contains(it.id)) {
                    it.copy(isCompleted = true, completedAt = now, updatedAt = now)
                } else it
            }
        } else {
            // Reopening (UNTICKING)
            val toReopen = mutableSetOf(itemId)

            // Rule 1: Unticking a parent -> all its recursive descendants become not done
            toReopen.addAll(getDescendantIds(itemId, itemsInList))

            // Rule 2: Unticking a subtask -> parent & all done ancestors become not done
            toReopen.addAll(getAncestorIds(itemId, itemsInList))

            affectedIds.addAll(toReopen)
            _items.value = allItems.map {
                if (toReopen.contains(it.id)) {
                    it.copy(
                        isCompleted = false,
                        completedAt = null,
                        updatedAt = now
                    )
                } else it
            }
        }

        recalculateStats()
        saveToDisk()

        val changedItems = _items.value.filter { affectedIds.contains(it.id) }
        scope.launch {
            SupabaseClient.upsertListItems(changedItems.map { it.toDto() })
        }
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

        val affected = updated.filter { it.listId == listId }
        scope.launch {
            SupabaseClient.upsertListItems(affected.map { it.toDto() })
        }
    }

    fun deleteListItem(itemId: String, deleteSubtasks: Boolean) {
        val allCurrent = _items.value
        val target = allCurrent.find { it.id == itemId } ?: return
        val idsToDelete = mutableSetOf<String>()
        val itemsToUpdate = mutableListOf<ListItem>()

        if (deleteSubtasks) {
            val descendantIds = getDescendantIds(itemId, allCurrent)
            idsToDelete.addAll(descendantIds)
            idsToDelete.add(itemId)
            _items.value = allCurrent.filter { !idsToDelete.contains(it.id) }
        } else {
            // Reparent direct children to target's parent
            idsToDelete.add(itemId)
            val updated = allCurrent.map {
                if (it.parentId == itemId) {
                    val reparented = it.copy(parentId = target.parentId, updatedAt = System.currentTimeMillis())
                    itemsToUpdate.add(reparented)
                    reparented
                } else it
            }.filter { it.id != itemId }
            _items.value = updated
        }

        recalculateStats()
        saveToDisk()

        scope.launch {
            if (itemsToUpdate.isNotEmpty()) {
                SupabaseClient.upsertListItems(itemsToUpdate.map { it.toDto() })
            }
            idsToDelete.forEach { id ->
                SupabaseClient.deleteListItem(id)
            }
        }
    }

    fun completeListItems(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        val allItems = _items.value
        val now = System.currentTimeMillis()
        val toComplete = mutableSetOf<String>()
        toComplete.addAll(itemIds)

        // 1. For each item, add all its recursive descendants
        for (id in itemIds) {
            toComplete.addAll(getDescendantIds(id, allItems))
        }

        // 2. Cascade upward: A parent becomes completed if ALL its direct children are completed
        val byId = allItems.associateBy { it.id }.toMutableMap()
        for (id in toComplete) {
            byId[id]?.let {
                byId[id] = it.copy(isCompleted = true, completedAt = now, updatedAt = now)
            }
        }

        // Check parents iteratively
        for (id in itemIds) {
            var currentParentId = byId[id]?.parentId
            val visited = mutableSetOf<String>()
            while (currentParentId != null && visited.add(currentParentId)) {
                val parentItem = byId[currentParentId] ?: break
                val siblings = byId.values.filter { it.parentId == currentParentId }
                val allSiblingsDone = siblings.isNotEmpty() && siblings.all { it.isCompleted || toComplete.contains(it.id) }
                if (allSiblingsDone) {
                    toComplete.add(currentParentId)
                    byId[currentParentId] = parentItem.copy(isCompleted = true, completedAt = now, updatedAt = now)
                    currentParentId = parentItem.parentId
                } else {
                    break
                }
            }
        }

        _items.value = allItems.map {
            if (toComplete.contains(it.id)) {
                it.copy(isCompleted = true, completedAt = now, updatedAt = now)
            } else it
        }

        recalculateStats()
        saveToDisk()

        val changedItems = _items.value.filter { toComplete.contains(it.id) }
        scope.launch {
            SupabaseClient.upsertListItems(changedItems.map { it.toDto() })
        }
    }

    fun deleteListItems(itemIds: Set<String>, deleteSubtasks: Boolean = true) {
        if (itemIds.isEmpty()) return
        val allCurrent = _items.value
        val idsToDelete = mutableSetOf<String>()
        idsToDelete.addAll(itemIds)

        if (deleteSubtasks) {
            for (id in itemIds) {
                idsToDelete.addAll(getDescendantIds(id, allCurrent))
            }
            _items.value = allCurrent.filter { !idsToDelete.contains(it.id) }
        } else {
            val byId = allCurrent.associateBy { it.id }
            val itemsToUpdate = mutableListOf<ListItem>()
            val updated = allCurrent.map { itm ->
                if (itm.parentId != null && itemIds.contains(itm.parentId)) {
                    val targetParent = byId[itm.parentId]?.parentId
                    val reparented = itm.copy(parentId = targetParent, updatedAt = System.currentTimeMillis())
                    itemsToUpdate.add(reparented)
                    reparented
                } else itm
            }.filter { !idsToDelete.contains(it.id) }
            _items.value = updated
            if (itemsToUpdate.isNotEmpty()) {
                scope.launch {
                    SupabaseClient.upsertListItems(itemsToUpdate.map { it.toDto() })
                }
            }
        }

        recalculateStats()
        saveToDisk()

        scope.launch {
            idsToDelete.forEach { id ->
                SupabaseClient.deleteListItem(id)
            }
        }
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

        val affectedItems = mutableListOf<ListItem>()
        if (moveSubtasks) {
            // Move target under newParentId; children remain attached to target
            val updated = allCurrent.map {
                if (it.id == itemId) {
                    val itm = it.copy(parentId = newParentId, updatedAt = System.currentTimeMillis())
                    affectedItems.add(itm)
                    itm
                } else it
            }
            _items.value = updated
        } else {
            // Leave subtasks attached to target's original parent, move only target
            val originalParent = target.parentId
            val updated = allCurrent.map {
                when (it.id) {
                    itemId -> {
                        val itm = it.copy(parentId = newParentId, updatedAt = System.currentTimeMillis())
                        affectedItems.add(itm)
                        itm
                    }
                    else -> {
                        if (it.parentId == itemId) {
                            val itm = it.copy(parentId = originalParent, updatedAt = System.currentTimeMillis())
                            affectedItems.add(itm)
                            itm
                        } else it
                    }
                }
            }
            _items.value = updated
        }

        recalculateStats()
        saveToDisk()

        scope.launch {
            SupabaseClient.upsertListItems(affectedItems.map { it.toDto() })
        }
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

    fun getAncestorItems(itemId: String, allItems: List<ListItem> = _items.value): List<ListItem> {
        val byId = allItems.associateBy { it.id }
        val ancestors = mutableListOf<ListItem>()
        var curr = byId[itemId]?.parentId
        val visited = mutableSetOf(itemId)
        while (curr != null && visited.add(curr)) {
            val parent = byId[curr] ?: break
            ancestors.add(0, parent)
            curr = parent.parentId
        }
        return ancestors
    }

    fun getBreadcrumbPath(
        itemId: String,
        allItems: List<ListItem> = _items.value,
        listName: String? = null
    ): List<String> {
        val item = allItems.find { it.id == itemId }
        val resolvedListName = listName ?: item?.let { itm -> _lists.value.find { it.id == itm.listId }?.name }
        val ancestors = getAncestorItems(itemId, allItems)
        val names = mutableListOf<String>()
        if (!resolvedListName.isNullOrBlank()) {
            names.add(resolvedListName)
        }
        names.addAll(ancestors.map { it.title })
        return names
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

        val reordered = updated.filter { it.listId == listId && updatedMap.containsKey(it.id) }
        scope.launch {
            SupabaseClient.upsertListItems(reordered.map { it.toDto() })
        }
    }

    fun moveItemUp(itemId: String) {
        val allItems = _items.value
        val target = allItems.find { it.id == itemId } ?: return
        val siblings = allItems.filter { it.listId == target.listId && it.parentId == target.parentId }
        val hasSubtasks = allItems.any { it.parentId == itemId }
        val peers = siblings.filter { (allItems.any { child -> child.parentId == it.id }) == hasSubtasks }
            .sortedBy { it.position }
            .toMutableList()
        val index = peers.indexOfFirst { it.id == itemId }
        if (index > 0) {
            val item = peers.removeAt(index)
            peers.add(index - 1, item)
            val updatedMap = peers.mapIndexed { idx, itm -> itm.id to idx }.toMap()
            val updated = allItems.map { itm ->
                if (updatedMap.containsKey(itm.id)) itm.copy(position = updatedMap[itm.id]!!) else itm
            }
            _items.value = updated
            saveToDisk()

            val changed = updated.filter { updatedMap.containsKey(it.id) }
            scope.launch {
                SupabaseClient.upsertListItems(changed.map { it.toDto() })
            }
        }
    }

    fun moveItemDown(itemId: String) {
        val allItems = _items.value
        val target = allItems.find { it.id == itemId } ?: return
        val siblings = allItems.filter { it.listId == target.listId && it.parentId == target.parentId }
        val hasSubtasks = allItems.any { it.parentId == itemId }
        val peers = siblings.filter { (allItems.any { child -> child.parentId == it.id }) == hasSubtasks }
            .sortedBy { it.position }
            .toMutableList()
        val index = peers.indexOfFirst { it.id == itemId }
        if (index != -1 && index < peers.size - 1) {
            val item = peers.removeAt(index)
            peers.add(index + 1, item)
            val updatedMap = peers.mapIndexed { idx, itm -> itm.id to idx }.toMap()
            val updated = allItems.map { itm ->
                if (updatedMap.containsKey(itm.id)) itm.copy(position = updatedMap[itm.id]!!) else itm
            }
            _items.value = updated
            saveToDisk()

            val changed = updated.filter { updatedMap.containsKey(it.id) }
            scope.launch {
                SupabaseClient.upsertListItems(changed.map { it.toDto() })
            }
        }
    }

    fun moveItemToTop(itemId: String) {
        val allItems = _items.value
        val target = allItems.find { it.id == itemId } ?: return
        val siblings = allItems.filter { it.listId == target.listId && it.parentId == target.parentId }
        val hasSubtasks = allItems.any { it.parentId == itemId }
        val peers = siblings.filter { (allItems.any { child -> child.parentId == it.id }) == hasSubtasks }
            .sortedBy { it.position }
            .toMutableList()
        val index = peers.indexOfFirst { it.id == itemId }
        if (index > 0) {
            val item = peers.removeAt(index)
            peers.add(0, item)
            val updatedMap = peers.mapIndexed { idx, itm -> itm.id to idx }.toMap()
            val updated = allItems.map { itm ->
                if (updatedMap.containsKey(itm.id)) itm.copy(position = updatedMap[itm.id]!!) else itm
            }
            _items.value = updated
            saveToDisk()

            val changed = updated.filter { updatedMap.containsKey(it.id) }
            scope.launch {
                SupabaseClient.upsertListItems(changed.map { it.toDto() })
            }
        }
    }

    fun moveItemToBottom(itemId: String) {
        val allItems = _items.value
        val target = allItems.find { it.id == itemId } ?: return
        val siblings = allItems.filter { it.listId == target.listId && it.parentId == target.parentId }
        val hasSubtasks = allItems.any { it.parentId == itemId }
        val peers = siblings.filter { (allItems.any { child -> child.parentId == it.id }) == hasSubtasks }
            .sortedBy { it.position }
            .toMutableList()
        val index = peers.indexOfFirst { it.id == itemId }
        if (index != -1 && index < peers.size - 1) {
            val item = peers.removeAt(index)
            peers.add(item)
            val updatedMap = peers.mapIndexed { idx, itm -> itm.id to idx }.toMap()
            val updated = allItems.map { itm ->
                if (updatedMap.containsKey(itm.id)) itm.copy(position = updatedMap[itm.id]!!) else itm
            }
            _items.value = updated
            saveToDisk()

            val changed = updated.filter { updatedMap.containsKey(it.id) }
            scope.launch {
                SupabaseClient.upsertListItems(changed.map { it.toDto() })
            }
        }
    }

    fun convertItemToStandaloneList(itemId: String): TaskList? {
        val allItems = _items.value
        val targetItem = allItems.find { it.id == itemId } ?: return null
        val currentLists = _lists.value
        val sourceListIndex = currentLists.indexOfFirst { it.id == targetItem.listId }
        val insertPos = if (sourceListIndex != -1) sourceListIndex + 1 else currentLists.size
        val userCode = getUserCodeSafe()

        val newList = TaskList(
            id = UUID.randomUUID().toString(),
            userCode = userCode,
            name = targetItem.title,
            iconName = targetItem.iconName,
            position = insertPos,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val descendantIds = getDescendantIds(itemId, allItems)
        val updatedLists = currentLists.toMutableList()
        updatedLists.add(insertPos, newList)
        val reorderedLists = updatedLists.mapIndexed { index, list -> list.copy(position = index) }
        _lists.value = reorderedLists

        val newItems = allItems.filter { it.id != itemId }.map { item ->
            if (descendantIds.contains(item.id)) {
                val newParentId = if (item.parentId == itemId) null else item.parentId
                item.copy(listId = newList.id, parentId = newParentId, updatedAt = System.currentTimeMillis())
            } else item
        }
        _items.value = newItems
        recalculateStats()
        saveToDisk()

        val migratedItems = newItems.filter { descendantIds.contains(it.id) }
        scope.launch {
            SupabaseClient.upsertTaskList(newList.toDto())
            SupabaseClient.deleteListItem(itemId)
            if (migratedItems.isNotEmpty()) {
                SupabaseClient.upsertListItems(migratedItems.map { it.toDto() })
            }
            reorderedLists.forEach { SupabaseClient.upsertTaskList(it.toDto()) }
        }

        return newList
    }
}
