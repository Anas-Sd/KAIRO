package com.kairo.app

import com.kairo.app.feature.tasks.alarm.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmSchedulerTest {

    @Test
    fun testCalculateTriggerMillis_specificDateAnd12HourTime() {
        val trigger = AlarmScheduler.calculateTriggerMillis(
            dueDate = "2026-10-25",
            dueTime = "10:30 AM",
            dueDateMillis = null
        )

        assertNotNull(trigger)
        val cal = Calendar.getInstance().apply { timeInMillis = trigger!! }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(25, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateTriggerMillis_evening12HourPM() {
        val trigger = AlarmScheduler.calculateTriggerMillis(
            dueDate = "2026-10-25",
            dueTime = "7:45 PM",
            dueDateMillis = null
        )

        assertNotNull(trigger)
        val cal = Calendar.getInstance().apply { timeInMillis = trigger!! }
        assertEquals(19, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testCalculateTriggerMillis_24HourTime() {
        val trigger = AlarmScheduler.calculateTriggerMillis(
            dueDate = "2026-10-25",
            dueTime = "21:15",
            dueDateMillis = null
        )

        assertNotNull(trigger)
        val cal = Calendar.getInstance().apply { timeInMillis = trigger!! }
        assertEquals(21, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testCalculateTriggerMillis_defaultMorningWhenNoTime() {
        val trigger = AlarmScheduler.calculateTriggerMillis(
            dueDate = "Tomorrow",
            dueTime = null,
            dueDateMillis = null
        )

        assertNotNull(trigger)
        val cal = Calendar.getInstance().apply { timeInMillis = trigger!! }
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testCalculateTriggerMillis_withDueDateMillis() {
        val explicitCal = Calendar.getInstance().apply {
            set(2027, Calendar.JANUARY, 15, 0, 0, 0)
        }
        val trigger = AlarmScheduler.calculateTriggerMillis(
            dueDate = "Irrelevant string",
            dueTime = "11:00 AM",
            dueDateMillis = explicitCal.timeInMillis
        )

        assertNotNull(trigger)
        val cal = Calendar.getInstance().apply { timeInMillis = trigger!! }
        assertEquals(2027, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(11, cal.get(Calendar.HOUR_OF_DAY))
    }
}
