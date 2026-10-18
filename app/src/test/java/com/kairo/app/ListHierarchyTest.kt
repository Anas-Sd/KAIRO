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
}
