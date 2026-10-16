package com.kairo.app

import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.ui.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringTaskReactivationTest {

    private fun createDailyRecurringTask(id: String, dueDate: String = "Today"): Task {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return Task(
            id = id,
            title = "Daily Workout",
            repeatType = "DAILY",
            priority = Priority.HIGH,
            dueDate = dueDate,
            dueDateMillis = todayStart,
            dueTime = "18:00",
            section = TaskSection.TODAY,
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testSnapshotIdConvention_andExtraction() {
        val masterId = "workout_101"
        val timestamp = 1729000000000L
        val snapshotId = "snapshot_${masterId}_$timestamp"

        assertTrue(snapshotId.startsWith("snapshot_"))
        val extractedMasterId = snapshotId.removePrefix("snapshot_").substringBeforeLast("_")
        assertEquals(masterId, extractedMasterId)
    }

    @Test
    fun testNextOccurrenceCalculation_forDailyTask() {
        val todayCal = Calendar.getInstance()
        val nextOccurrence = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = todayCal.timeInMillis,
            currentDueDateStr = "Today",
            repeatType = "DAILY",
            repeatDays = null,
            repeatDates = null
        )

        assertNotNull(nextOccurrence)
        assertTrue(nextOccurrence!!.second > todayCal.timeInMillis)
        assertEquals(DateUtils.formatDisplayDate(null, nextOccurrence.second), nextOccurrence.first)
    }

    @Test
    fun testSimulation_completeAndUntickSnapshot_rewindsMasterToToday() {
        val now = System.currentTimeMillis()
        val master = createDailyRecurringTask("task_daily_1")
        val allTasks = mutableListOf(master)

        // 1. User completes task prematurely before today's scheduled time
        val (nextDueDateStr, nextDueDateMillis) = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = master.dueDateMillis,
            currentDueDateStr = master.dueDate,
            repeatType = master.repeatType,
            repeatDays = null,
            repeatDates = null
        )!!

        val snapshotId = "snapshot_${master.id}_$now"
        val completedSnapshot = master.copy(
            id = snapshotId,
            isCompleted = true,
            dueDate = master.dueDate,
            section = TaskSection.COMPLETED,
            completedAt = now,
            repeatType = null
        )

        val rolledOverMaster = master.copy(
            dueDate = nextDueDateStr,
            dueDateMillis = nextDueDateMillis,
            section = TaskSection.UPCOMING,
            isCompleted = false
        )

        allTasks.clear()
        allTasks.add(completedSnapshot)
        allTasks.add(rolledOverMaster)

        assertEquals(2, allTasks.size)
        val upcomingTask = allTasks.find { it.id == master.id }!!
        assertEquals(nextDueDateStr, upcomingTask.dueDate)
        assertEquals(TaskSection.UPCOMING, upcomingTask.section)

        // 2. User unticks the completed snapshot
        val originTaskId = completedSnapshot.id.removePrefix("snapshot_").substringBeforeLast("_")
        assertEquals(master.id, originTaskId)

        val targetMaster = allTasks.find { it.id == originTaskId }!!
        val rewoundMaster = targetMaster.copy(
            dueDate = completedSnapshot.dueDate,
            dueDateMillis = completedSnapshot.dueDateMillis,
            isCompleted = false,
            completedAt = null,
            section = TaskSection.TODAY,
            updatedAt = System.currentTimeMillis()
        )

        val finalTasks = allTasks.filterNot { it.id == completedSnapshot.id }.map {
            if (it.id == originTaskId) rewoundMaster else it
        }

        // 3. Verify snapshot is removed and master is back in TODAY
        assertEquals(1, finalTasks.size)
        val restoredTask = finalTasks.first()
        assertEquals(master.id, restoredTask.id)
        assertEquals("Today", restoredTask.dueDate)
        assertEquals(TaskSection.TODAY, restoredTask.section)
        assertEquals(false, restoredTask.isCompleted)
        assertEquals("DAILY", restoredTask.repeatType)
    }
}
