package com.kairo.app

import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.ui.viewmodel.TasksUiState
import com.kairo.app.ui.viewmodel.UiToggles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskTomorrowUpcomingTest {

    private fun getTodayBounds(): Pair<Long, Long> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Pair(todayStart, todayStart + 86400000L)
    }

    private fun getTomorrowBounds(): Pair<Long, Long> {
        val tomorrowCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val tomorrowStart = tomorrowCal.timeInMillis
        val dayAfterCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 2)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val tomorrowEnd = dayAfterCal.timeInMillis
        return Pair(tomorrowStart, tomorrowEnd)
    }

    private fun isToday(task: Task): Boolean {
        if (task.isCompleted) return false
        val (todayStart, todayEnd) = getTodayBounds()
        if (task.dueDate.contains("Today", ignoreCase = true)) return true
        val taskDue = task.dueDateMillis ?: 0L
        if (task.dueDate.isNotBlank() || task.dueDateMillis != null) {
            return taskDue in todayStart until todayEnd
        }
        return task.section == TaskSection.TODAY
    }

    private fun isTomorrow(task: Task): Boolean {
        if (task.isCompleted) return false
        if (task.dueDate.contains("Tomorrow", ignoreCase = true)) return true
        val (tomorrowStart, tomorrowEnd) = getTomorrowBounds()
        if (task.dueDate.isNotBlank() || task.dueDateMillis != null) {
            val taskDue = task.dueDateMillis ?: 0L
            return taskDue in tomorrowStart until tomorrowEnd
        }
        return false
    }

    private fun isUpcoming(task: Task): Boolean {
        if (task.isCompleted) return false
        if (isToday(task)) return false
        if (isTomorrow(task)) return false
        return true
    }

    @Test
    fun testTasksUiState_defaultsIncludeTomorrow() {
        val state = TasksUiState()
        assertTrue(state.tomorrowTasks.isEmpty())
        assertFalse(state.tomorrowExpanded)

        val toggles = UiToggles()
        assertFalse(toggles.tomorrowExpanded)
    }

    @Test
    fun testTomorrowClassification_tomorrowString() {
        val task = Task(
            id = "tom-1",
            title = "Task for tomorrow",
            dueDate = "Tomorrow",
            createdAt = System.currentTimeMillis()
        )
        assertTrue(isTomorrow(task))
        assertFalse(isToday(task))
        assertFalse(isUpcoming(task))
    }

    @Test
    fun testTomorrowClassification_tomorrowTimestamp() {
        val (tomorrowStart, _) = getTomorrowBounds()
        val task = Task(
            id = "tom-2",
            title = "Task for tomorrow noon",
            dueDate = "2026-10-24",
            dueDateMillis = tomorrowStart + 3600000L * 12,
            createdAt = System.currentTimeMillis()
        )
        assertTrue(isTomorrow(task))
        assertFalse(isToday(task))
        assertFalse(isUpcoming(task))
    }

    @Test
    fun testUpcomingClassification_futureDaysStayInUpcoming() {
        val (_, tomorrowEnd) = getTomorrowBounds()
        // Day after tomorrow
        val taskDayAfter = Task(
            id = "up-1",
            title = "Task day after tomorrow",
            dueDate = "Oct 25",
            dueDateMillis = tomorrowEnd + 3600000L * 5,
            createdAt = System.currentTimeMillis()
        )
        assertFalse(isTomorrow(taskDayAfter))
        assertFalse(isToday(taskDayAfter))
        assertTrue(isUpcoming(taskDayAfter))

        // Next week
        val taskNextWeek = Task(
            id = "up-2",
            title = "Task next week",
            dueDate = "Oct 30",
            dueDateMillis = tomorrowEnd + 86400000L * 7,
            createdAt = System.currentTimeMillis()
        )
        assertFalse(isTomorrow(taskNextWeek))
        assertFalse(isToday(taskNextWeek))
        assertTrue(isUpcoming(taskNextWeek))
    }

    @Test
    fun testPartition_todayTomorrowUpcomingCompleted() {
        val (todayStart, _) = getTodayBounds()
        val (tomorrowStart, _) = getTomorrowBounds()
        val dayAfterTomorrow = tomorrowStart + 86400000L * 2

        val taskToday = Task(id = "1", title = "Today", dueDate = "Today", dueDateMillis = todayStart + 3600000L)
        val taskTomorrow = Task(id = "2", title = "Tomorrow", dueDate = "Tomorrow", dueDateMillis = tomorrowStart + 3600000L)
        val taskDayAfter = Task(id = "3", title = "Day After", dueDate = "Future", dueDateMillis = dayAfterTomorrow)
        val taskCompleted = Task(id = "4", title = "Completed", isCompleted = true, dueDate = "Tomorrow")

        val list = listOf(taskToday, taskTomorrow, taskDayAfter, taskCompleted)

        val today = list.filter { isToday(it) }
        val tomorrow = list.filter { isTomorrow(it) }
        val upcoming = list.filter { isUpcoming(it) }

        assertEquals(listOf(taskToday), today)
        assertEquals(listOf(taskTomorrow), tomorrow)
        assertEquals(listOf(taskDayAfter), upcoming)
    }
}
