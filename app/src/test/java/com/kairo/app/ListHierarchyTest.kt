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
}
