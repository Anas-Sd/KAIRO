package com.kairo.app.feature.habits.model

import java.time.LocalDate

enum class HabitType {
    YES_NO,
    TARGET
}

enum class HabitRepeatType {
    EVERYDAY,
    SPECIFIC_DAYS,
    WEEKLY_X
}

enum class HabitDayState {
    UNMARKED,
    DONE,
    MISSED
}

data class HabitDayRecord(
    val state: HabitDayState = HabitDayState.UNMARKED,
    val value: Int = 0
)

data class Habit(
    val id: String,
    val userCode: String,
    val title: String,
    val type: HabitType = HabitType.YES_NO,
    val targetValue: Int = 1,
    val targetUnit: String = "",
    val repeatType: HabitRepeatType = HabitRepeatType.EVERYDAY,
    val repeatDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon .. 7=Sun
    val weeklyTargetCount: Int = 3,
    val reminderEnabled: Boolean = false,
    val reminderTime: String? = null,
    val reminderToneUri: String? = null,
    val reminderToneTitle: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class HabitDayInfo(
    val date: LocalDate,
    val dayNumber: Int,
    val dayLabel: String,
    val isToday: Boolean,
    val state: HabitDayState,
    val value: Int = 0,
    val isRestDay: Boolean = false
)

data class HabitWithStats(
    val habit: Habit,
    val streak: Int,
    val bestStreak: Int,
    val doneCount: Int,
    val missedCount: Int,
    val adherenceRate: Float,
    val days: List<HabitDayInfo>,
    val todayValue: Int = 0,
    val todayProgressFraction: Float = 0f,
    val isTodayScheduled: Boolean = true,
    val weeklyDoneThisWeek: Int = 0
)

data class DisciplineOverviewStats(
    val completedToday: Int,
    val totalToday: Int,
    val percentage: Int,
    val remainingToday: Int,
    val streakDays: Int
)
