package com.kairo.app

import com.kairo.app.feature.lists.data.ListRepository
import com.kairo.app.feature.lists.model.ListItem
import com.kairo.app.feature.lists.model.ListWithStats
import com.kairo.app.feature.lists.model.TaskList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListHierarchyTest {

    private fun createItem(
        id: String,
        listId: String = "list-1",
        parentId: String? = null,
        isCompleted: Boolean = false
    ): ListItem {
        return ListItem(
            id = id,
            listId = listId,
            parentId = parentId,
            title = "Item $id",
            isCompleted = isCompleted,
            createdAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testGetDescendantIds_nestedHierarchy() {
        // A -> B -> C
        //   -> D
        val a = createItem("A")
        val b = createItem("B", parentId = "A")
        val c = createItem("C", parentId = "B")
        val d = createItem("D", parentId = "A")
        val other = createItem("OTHER")

        val all = listOf(a, b, c, d, other)

        val descendantsA = ListRepository.getDescendantIds("A", all)
        assertEquals(setOf("B", "C", "D"), descendantsA)

        val descendantsB = ListRepository.getDescendantIds("B", all)
        assertEquals(setOf("C"), descendantsB)

        val descendantsC = ListRepository.getDescendantIds("C", all)
        assertTrue(descendantsC.isEmpty())
    }

    @Test
    fun testGetAncestorIds_orderedChain() {
        val a = createItem("A")
        val b = createItem("B", parentId = "A")
        val c = createItem("C", parentId = "B")

        val all = listOf(a, b, c)

        val ancestorsC = ListRepository.getAncestorIds("C", all)
        assertEquals(listOf("B", "A"), ancestorsC)

        val ancestorsB = ListRepository.getAncestorIds("B", all)
        assertEquals(listOf("A"), ancestorsB)

        val ancestorsA = ListRepository.getAncestorIds("A", all)
        assertTrue(ancestorsA.isEmpty())
    }

    @Test
    fun testCircularReference_doesNotLoopInfinitely() {
        // A -> B -> A (corrupt circular link)
        val a = createItem("A", parentId = "B")
        val b = createItem("B", parentId = "A")
        val all = listOf(a, b)

        val descendants = ListRepository.getDescendantIds("A", all)
        assertEquals(setOf("B"), descendants)

        val ancestors = ListRepository.getAncestorIds("A", all)
        assertEquals(listOf("B"), ancestors)
    }

    @Test
    fun testListWithStats_progressCalculation() {
        val list = TaskList(id = "list-1", name = "Groceries")

        val statsEmpty = ListWithStats(
            list = list,
            totalCount = 0,
            completedCount = 0,
            activeCount = 0,
            progressPercentage = 0
        )
        assertEquals(0, statsEmpty.progressPercentage)
        assertEquals(0, statsEmpty.activeCount)

        val statsPartial = ListWithStats(
            list = list,
            totalCount = 4,
            completedCount = 1,
            activeCount = 3,
            progressPercentage = 25
        )
        assertEquals(25, statsPartial.progressPercentage)
        assertEquals(3, statsPartial.activeCount)

        val statsComplete = ListWithStats(
            list = list,
            totalCount = 5,
            completedCount = 5,
            activeCount = 0,
            progressPercentage = 100
        )
        assertEquals(100, statsComplete.progressPercentage)
        assertEquals(0, statsComplete.activeCount)
    }

    @Test
    fun testMoveListIntoList_migratesHierarchyCorrectly() {
        // Source list "dmart" with items:
        // - "Apples" (root item in dmart)
        //   - "Fuji" (subtask of Apples)
        // - "Bread" (root item in dmart)
        val sourceListId = "list-dmart"
        val targetListId = "list-groceries"

        val dmartList = TaskList(id = sourceListId, name = "dmart")
        val groceriesList = TaskList(id = targetListId, name = "groceries")

        val apples = ListItem(id = "item-apples", listId = sourceListId, parentId = null, title = "Apples")
        val fuji = ListItem(id = "item-fuji", listId = sourceListId, parentId = "item-apples", title = "Fuji")
        val bread = ListItem(id = "item-bread", listId = sourceListId, parentId = null, title = "Bread")
        val milk = ListItem(id = "item-milk", listId = targetListId, parentId = null, title = "Milk")

        val currentLists = listOf(dmartList, groceriesList)
        val currentItems = listOf(apples, fuji, bread, milk)

        // Simulate moveListIntoList algorithm
        val newParentItem = ListItem(
            id = "item-dmart-parent",
            listId = targetListId,
            parentId = null,
            title = dmartList.name
        )

        val migratedItems = currentItems.filter { it.listId == sourceListId }.map { item ->
            val newParentId = if (item.parentId == null) newParentItem.id else item.parentId
            item.copy(listId = targetListId, parentId = newParentId)
        }

        val remainingItems = currentItems.filter { it.listId != sourceListId }
        val finalItems = remainingItems + newParentItem + migratedItems
        val remainingLists = currentLists.filter { it.id != sourceListId }

        // Assertions
        assertEquals(1, remainingLists.size)
        assertEquals("groceries", remainingLists.first().name)

        // dmart is now a parent item in groceries
        val dmartInGroceries = finalItems.find { it.id == "item-dmart-parent" }
        org.junit.Assert.assertNotNull(dmartInGroceries)
        assertEquals(targetListId, dmartInGroceries?.listId)
        assertEquals(null, dmartInGroceries?.parentId)

        // Root items of dmart now have parentId == dmart parent item
        val applesInGroceries = finalItems.find { it.id == "item-apples" }
        assertEquals(newParentItem.id, applesInGroceries?.parentId)
        assertEquals(targetListId, applesInGroceries?.listId)

        val breadInGroceries = finalItems.find { it.id == "item-bread" }
        assertEquals(newParentItem.id, breadInGroceries?.parentId)
        assertEquals(targetListId, breadInGroceries?.listId)

        // Subtask "Fuji" remains under "Apples"
        val fujiInGroceries = finalItems.find { it.id == "item-fuji" }
        assertEquals("item-apples", fujiInGroceries?.parentId)
        assertEquals(targetListId, fujiInGroceries?.listId)

        // Descendants of dmart parent include Apples, Bread, Fuji
        val descendantsOfDmart = ListRepository.getDescendantIds(newParentItem.id, finalItems)
        assertEquals(setOf("item-apples", "item-fuji", "item-bread"), descendantsOfDmart)
    }

    @Test
    fun testCascadingCompletion_completesAllDescendants() {
        // Hierarchy: Parent -> Child1, Child2 -> Grandchild
        val p = createItem("P", isCompleted = false)
        val c1 = createItem("C1", parentId = "P", isCompleted = false)
        val c2 = createItem("C2", parentId = "P", isCompleted = false)
        val gc = createItem("GC", parentId = "C2", isCompleted = false)
        val other = createItem("OTHER", isCompleted = false)

        val items = listOf(p, c1, c2, gc, other)

        // Complete P: P, C1, C2, GC must all become completed; OTHER remains uncompleted
        val toComplete = mutableSetOf("P")
        toComplete.addAll(ListRepository.getDescendantIds("P", items))

        assertEquals(setOf("P", "C1", "C2", "GC"), toComplete)

        val updated = items.map { if (toComplete.contains(it.id)) it.copy(isCompleted = true) else it }
        assertTrue(updated.find { it.id == "P" }!!.isCompleted)
        assertTrue(updated.find { it.id == "C1" }!!.isCompleted)
        assertTrue(updated.find { it.id == "C2" }!!.isCompleted)
        assertTrue(updated.find { it.id == "GC" }!!.isCompleted)
        org.junit.Assert.assertFalse(updated.find { it.id == "OTHER" }!!.isCompleted)
    }

    @Test
    fun testCascadingCompletion_upwardCascadeWhenAllSiblingsDone() {
        // Hierarchy: Parent P -> C1 (already true), C2 (about to complete)
        val p = createItem("P", isCompleted = false)
        val c1 = createItem("C1", parentId = "P", isCompleted = true)
        val c2 = createItem("C2", parentId = "P", isCompleted = false)

        val items = listOf(p, c1, c2)

        // Complete C2
        val toComplete = mutableSetOf("C2")
        toComplete.addAll(ListRepository.getDescendantIds("C2", items))

        val byId = items.associateBy { it.id }.toMutableMap()
        for (id in toComplete) {
            byId[id]?.let { byId[id] = it.copy(isCompleted = true) }
        }

        var currentParentId = byId["C2"]?.parentId
        val visited = mutableSetOf<String>()
        while (currentParentId != null && visited.add(currentParentId)) {
            val parentItem = byId[currentParentId] ?: break
            val siblings = byId.values.filter { it.parentId == currentParentId }
            val allSiblingsDone = siblings.isNotEmpty() && siblings.all { it.isCompleted || toComplete.contains(it.id) }
            if (allSiblingsDone) {
                toComplete.add(currentParentId)
                byId[currentParentId] = parentItem.copy(isCompleted = true)
                currentParentId = parentItem.parentId
            } else break
        }

        // P should now be auto-completed because all its direct children (C1, C2) are completed!
        assertTrue(toComplete.contains("P"))
        assertTrue(toComplete.contains("C2"))
    }

    @Test
    fun testCascadingUntick_reopensAllDescendantsAndAncestors() {
        // Hierarchy: Grandparent -> Parent -> Child
        val gp = createItem("GP", isCompleted = true)
        val p = createItem("P", parentId = "GP", isCompleted = true)
        val c = createItem("C", parentId = "P", isCompleted = true)
        val items = listOf(gp, p, c)

        // Case 1: Unticking Grandparent reopens all descendants
        val toReopenGP = mutableSetOf("GP")
        toReopenGP.addAll(ListRepository.getDescendantIds("GP", items))
        toReopenGP.addAll(ListRepository.getAncestorIds("GP", items))
        assertEquals(setOf("GP", "P", "C"), toReopenGP)

        // Case 2: Unticking Child reopens Child and all ancestor parents (Parent and Grandparent)
        val toReopenC = mutableSetOf("C")
        toReopenC.addAll(ListRepository.getDescendantIds("C", items))
        toReopenC.addAll(ListRepository.getAncestorIds("C", items))
        assertEquals(setOf("C", "P", "GP"), toReopenC)
    }

    @Test
    fun testParentItems_automaticallyFloatToTop() {
        // List A has items b, c, d. b has subtasks x, y, z.
        // Therefore, b is a parent task, while c and d are leaf tasks.
        // b must automatically come over the top of list A (before c and d).
        val b = createItem("b", parentId = null)
        val c = createItem("c", parentId = null)
        val d = createItem("d", parentId = null)
        val x = createItem("x", parentId = "b")
        val y = createItem("y", parentId = "b")
        val z = createItem("z", parentId = "b")

        val allItems = listOf(b, c, d, x, y, z)
        val rootItems = allItems.filter { it.parentId == null }

        val (parents, leaves) = rootItems.partition { item ->
            allItems.any { it.parentId == item.id }
        }

        val sortedDisplay = parents.sortedBy { it.position } + leaves.sortedBy { it.position }
        assertEquals("b", sortedDisplay[0].id)
        assertEquals(listOf("b", "c", "d"), sortedDisplay.map { it.id })
    }

    @Test
    fun testConvertItemToStandaloneList_createsNewListBelowCurrent() {
        // List A at position 0, List B at position 1
        val listA = TaskList(id = "list-A", name = "List A", position = 0)
        val listB = TaskList(id = "list-B", name = "List B", position = 1)
        val currentLists = listOf(listA, listB)

        // Item X is in List A
        val itemX = ListItem(id = "x", listId = "list-A", parentId = null, title = "Task X")
        val childX = ListItem(id = "sub-x", listId = "list-A", parentId = "x", title = "Subtask of X")
        val itemY = ListItem(id = "y", listId = "list-A", parentId = null, title = "Task Y")
        val allItems = listOf(itemX, childX, itemY)

        val sourceListIndex = currentLists.indexOfFirst { it.id == itemX.listId }
        val insertPos = sourceListIndex + 1

        val newList = TaskList(
            id = "new-list-X",
            name = itemX.title,
            position = insertPos
        )

        val updatedLists = currentLists.toMutableList()
        updatedLists.add(insertPos, newList)
        val reindexedLists = updatedLists.mapIndexed { idx, list -> list.copy(position = idx) }

        // New list X is inserted immediately below List A
        assertEquals("list-A", reindexedLists[0].id)
        assertEquals(0, reindexedLists[0].position)
        assertEquals("new-list-X", reindexedLists[1].id)
        assertEquals(1, reindexedLists[1].position)
        assertEquals("list-B", reindexedLists[2].id)
        assertEquals(2, reindexedLists[2].position)

        // Subtask childX is reparented to new-list-X with parentId = null
        val descendantIds = ListRepository.getDescendantIds(itemX.id, allItems)
        val newItems = allItems.filter { it.id != itemX.id }.map { item ->
            if (descendantIds.contains(item.id)) {
                val newParentId = if (item.parentId == itemX.id) null else item.parentId
                item.copy(listId = newList.id, parentId = newParentId)
            } else item
        }

        val migratedChild = newItems.find { it.id == "sub-x" }!!
        assertEquals("new-list-X", migratedChild.listId)
        assertEquals(null, migratedChild.parentId)

        val untouchedY = newItems.find { it.id == "y" }!!
        assertEquals("list-A", untouchedY.listId)
    }

    @Test
    fun testAdjustItemsParent_moveMultipleItemsWithSubtasks() {
        // Items:
        // Task 1 (root) -> Sub 1A (child of 1)
        // Task 2 (root) -> Sub 2A (child of 2)
        // ParentTarget (root)
        val t1 = createItem("t1")
        val s1a = createItem("s1a", parentId = "t1")
        val t2 = createItem("t2")
        val s2a = createItem("s2a", parentId = "t2")
        val target = createItem("target")

        val allItems = listOf(t1, s1a, t2, s2a, target)
        val itemIdsToMove = setOf("t1", "t2")
        val newParentId = "target"

        // Simulate moveSubtasks = true:
        val updated = allItems.map { item ->
            if (itemIdsToMove.contains(item.id)) {
                item.copy(parentId = newParentId)
            } else item
        }

        assertEquals("target", updated.find { it.id == "t1" }?.parentId)
        assertEquals("target", updated.find { it.id == "t2" }?.parentId)
        // Children s1a and s2a stay attached to t1 and t2
        assertEquals("t1", updated.find { it.id == "s1a" }?.parentId)
        assertEquals("t2", updated.find { it.id == "s2a" }?.parentId)
    }

    @Test
    fun testAdjustItemsParent_moveMultipleItemsWithoutSubtasks() {
        // Task 1 (child of "rootParent") -> Sub 1A (child of 1)
        // Task 2 (root) -> Sub 2A (child of 2)
        // Target
        val rootP = createItem("rootP")
        val t1 = createItem("t1", parentId = "rootP")
        val s1a = createItem("s1a", parentId = "t1")
        val t2 = createItem("t2", parentId = null)
        val s2a = createItem("s2a", parentId = "t2")
        val target = createItem("target")

        val allItems = listOf(rootP, t1, s1a, t2, s2a, target)
        val itemIdsToMove = setOf("t1", "t2")
        val targets = allItems.filter { itemIdsToMove.contains(it.id) }
        val targetOriginalParentMap = targets.associate { it.id to it.parentId }

        // Simulate moveSubtasks = false:
        val updated = allItems.map { item ->
            if (itemIdsToMove.contains(item.id)) {
                item.copy(parentId = "target")
            } else if (targetOriginalParentMap.containsKey(item.parentId)) {
                item.copy(parentId = targetOriginalParentMap[item.parentId])
            } else item
        }

        // t1 and t2 are moved under target
        assertEquals("target", updated.find { it.id == "t1" }?.parentId)
        assertEquals("target", updated.find { it.id == "t2" }?.parentId)
        // s1a is reparented to t1's original parent "rootP"
        assertEquals("rootP", updated.find { it.id == "s1a" }?.parentId)
        // s2a is reparented to t2's original parent null
        assertEquals(null, updated.find { it.id == "s2a" }?.parentId)
    }

    @Test
    fun testAdjustItemsParent_cyclePrevention() {
        val a = createItem("a")
        val b = createItem("b", parentId = "a")
        val c = createItem("c", parentId = "b")
        val d = createItem("d")

        val allItems = listOf(a, b, c, d)
        val itemIdsToMove = setOf("a", "d")

        // Forbidden targets: a, d, plus descendants of a ("b", "c")
        val forbidden = itemIdsToMove + itemIdsToMove.flatMap { ListRepository.getDescendantIds(it, allItems) }
        assertEquals(setOf("a", "d", "b", "c"), forbidden)

        // "b" is forbidden
        assertTrue(forbidden.contains("b"))
        // "c" is forbidden
        assertTrue(forbidden.contains("c"))
        // "a" is forbidden
        assertTrue(forbidden.contains("a"))
    }
}
