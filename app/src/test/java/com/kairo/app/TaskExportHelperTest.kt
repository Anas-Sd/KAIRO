package com.kairo.app

import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.feature.tasks.export.TaskExportHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskExportHelperTest {

    @Test
    fun testGenerateCsv_escapingAndFormatting() {
        val taskWithSpecialChars = Task(
            id = "task-1",
            title = "Buy milk, eggs & \"bread\"",
            notes = "First line\nSecond line with \"quotes\", and commas",
            priority = Priority.HIGH,
            dueDate = "Oct 25",
            dueTime = "10:00 AM",
            location = "Supermarket, New York",
            section = TaskSection.TODAY,
            isCompleted = false,
            createdAt = 1729000000000L
        )

        val csv = TaskExportHelper.generateCsv(listOf(taskWithSpecialChars), listOf(taskWithSpecialChars))

        // Check header exists (headers are quoted in CSV)
        assertTrue(csv.contains("\"Task ID\",\"Title\",\"Status\",\"Section\",\"Priority\""))

        // Check escaping of title quotes
        assertTrue(csv.contains("\"\"bread\"\""))

        // Check escaping of notes
        assertTrue(csv.contains("\"\"quotes\"\""))
        assertTrue(csv.contains("First line"))
        assertTrue(csv.contains("Second line with"))

        // Check location escaping
        assertTrue(csv.contains("\"Supermarket, New York\""))
    }

    @Test
    fun testGenerateCsv_sanitizesSnoozedDueDate() {
        val snoozedTask = Task(
            id = "task-2",
            title = "Test Snoozed",
            dueDate = "SNOOZED",
            dueDateMillis = 1729000000000L,
            priority = Priority.LOW,
            section = TaskSection.COMPLETED,
            isCompleted = true,
            createdAt = 1729000000000L,
            completedAt = 1729001000000L
        )

        val csv = TaskExportHelper.generateCsv(listOf(snoozedTask), listOf(snoozedTask))

        // SNOOZED string must NOT be in the due date column!
        assertFalse(csv.contains(",\"SNOOZED\","))
        assertFalse(csv.contains("\"SNOOZED\""))
    }

    @Test
    fun testGenerateHumanReadable_includesSectionsAndSubtasks() {
        val parent = Task(
            id = "parent-1",
            title = "Plan trip",
            priority = Priority.HIGH,
            dueDate = "Tomorrow",
            section = TaskSection.TODAY,
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )
        val child = Task(
            id = "child-1",
            parentId = "parent-1",
            title = "Book flights",
            priority = Priority.HIGH,
            dueDate = "Tomorrow",
            section = TaskSection.TODAY,
            isCompleted = true,
            createdAt = System.currentTimeMillis(),
            completedAt = System.currentTimeMillis()
        )

        val text = TaskExportHelper.generateHumanReadable(
            tasks = listOf(parent, child),
            allTasks = listOf(parent, child),
            isFiltered = false
        )

        assertTrue(text.contains("KAIRO TASKS EXPORT"))
        assertTrue(text.contains("[PENDING TASKS] (1)"))
        assertTrue(text.contains("[COMPLETED TASKS] (1)"))
        assertTrue(text.contains("Plan trip"))
        assertTrue(text.contains("Book flights"))
        assertTrue(text.contains("Subtasks (1):"))
        assertTrue(text.contains("[✓] Book flights"))
    }
}
