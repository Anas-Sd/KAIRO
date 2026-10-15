package com.kairo.app

import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.data.repository.TaskRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskHierarchyTest {

    private fun createTask(id: String, parentId: String? = null, isCompleted: Boolean = false): Task {
        return Task(
            id = id,
            title = "Task $id",
            parentId = parentId,
            priority = Priority.MEDIUM,
            dueDate = "Today",
            section = TaskSection.TODAY,
            isCompleted = isCompleted,
            createdAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testGetDescendantIds_nestedHierarchy() {
        // A -> B -> C
        //   -> D
        val a = createTask("A")
        val b = createTask("B", parentId = "A")
        val c = createTask("C", parentId = "B")
        val d = createTask("D", parentId = "A")
        val other = createTask("OTHER")

        val all = listOf(a, b, c, d, other)

        val descendantsA = TaskRepository.getDescendantIds("A", all)
        assertEquals(setOf("B", "C", "D"), descendantsA)

        val descendantsB = TaskRepository.getDescendantIds("B", all)
        assertEquals(setOf("C"), descendantsB)

        val descendantsC = TaskRepository.getDescendantIds("C", all)
        assertTrue(descendantsC.isEmpty())
    }

    @Test
    fun testGetAncestorIds_orderedFromImmediateParentToRoot() {
        val a = createTask("A")
        val b = createTask("B", parentId = "A")
        val c = createTask("C", parentId = "B")

        val all = listOf(a, b, c)

        val ancestorsC = TaskRepository.getAncestorIds("C", all)
        assertEquals(listOf("B", "A"), ancestorsC)

        val ancestorsB = TaskRepository.getAncestorIds("B", all)
        assertEquals(listOf("A"), ancestorsB)

        val ancestorsA = TaskRepository.getAncestorIds("A", all)
        assertTrue(ancestorsA.isEmpty())
    }

    @Test
    fun testCircularReference_doesNotLoopInfinitely() {
        // A -> B -> A (corrupt circular link)
        val a = createTask("A", parentId = "B")
        val b = createTask("B", parentId = "A")
        val all = listOf(a, b)

        val descendants = TaskRepository.getDescendantIds("A", all)
        assertEquals(setOf("B"), descendants)

        val ancestors = TaskRepository.getAncestorIds("A", all)
        assertEquals(listOf("B"), ancestors)
    }

    @Test
    fun testSubtaskProgress() {
        val parent = createTask("P")
        val c1 = createTask("C1", parentId = "P", isCompleted = true)
        val c2 = createTask("C2", parentId = "P", isCompleted = false)
        val c3 = createTask("C3", parentId = "P", isCompleted = true)
        val all = listOf(parent, c1, c2, c3)

        val progress = TaskRepository.getSubtaskProgress("P", all)
        assertEquals(Pair(2, 3), progress)
    }
}
