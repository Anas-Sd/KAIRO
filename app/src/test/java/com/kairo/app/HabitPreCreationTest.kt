package com.kairo.app

import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitPreCreationTest {

    @Test
    fun testGetEffectiveStartDate_withExplicitStartDate() {
        val habit = Habit(
            id = "habit-1",
            userCode = "test-user",
            title = "Morning Run",
            startDate = "2026-10-10",
            createdAt = 1791800000000L
        )

        val startDate = HabitRepository.getEffectiveStartDate(habit)
        assertEquals(LocalDate.of(2026, 10, 10), startDate)
    }

    @Test
    fun testGetEffectiveStartDate_fallbackToCreatedAt() {
        // 1791800000000L is in October 2026
        val habit = Habit(
            id = "habit-2",
            userCode = "test-user",
            title = "Read Book",
            startDate = "",
            createdAt = 1791800000000L
        )

        val startDate = HabitRepository.getEffectiveStartDate(habit)
        assertEquals(2026, startDate.year)
        assertEquals(10, startDate.monthValue)
    }

    @Test
    fun testPreCreationDate_markedAsBeforeStart() {
        val habitStartDate = LocalDate.of(2026, 10, 15)

        val dateBefore = LocalDate.of(2026, 10, 14)
        val dateSame = LocalDate.of(2026, 10, 15)
        val dateAfter = LocalDate.of(2026, 10, 16)

        assertTrue(dateBefore.isBefore(habitStartDate))
        assertFalse(dateSame.isBefore(habitStartDate))
        assertFalse(dateAfter.isBefore(habitStartDate))
    }

    @Test
    fun testRestDayDetection() {
        // Specific days: Mon, Wed, Fri (1, 3, 5)
        val habit = Habit(
            id = "habit-3",
            userCode = "test-user",
            title = "Gym Workout",
            repeatType = HabitRepeatType.SPECIFIC_DAYS,
            repeatDays = listOf(1, 3, 5)
        )

        // Tuesday (2) should be rest day
        val tuesday = LocalDate.of(2026, 10, 13) // Tuesday
        assertEquals(2, tuesday.dayOfWeek.value)
        val isTuesdayRest = habit.repeatType == HabitRepeatType.SPECIFIC_DAYS && !habit.repeatDays.contains(tuesday.dayOfWeek.value)
        assertTrue(isTuesdayRest)

        // Wednesday (3) should NOT be rest day
        val wednesday = LocalDate.of(2026, 10, 14) // Wednesday
        assertEquals(3, wednesday.dayOfWeek.value)
        val isWednesdayRest = habit.repeatType == HabitRepeatType.SPECIFIC_DAYS && !habit.repeatDays.contains(wednesday.dayOfWeek.value)
        assertFalse(isWednesdayRest)
    }
}
