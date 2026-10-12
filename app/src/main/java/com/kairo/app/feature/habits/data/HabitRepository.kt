package com.kairo.app.feature.habits.data

import android.content.Context
import android.content.SharedPreferences
import com.kairo.app.KairoApplication
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.feature.habits.model.DisciplineOverviewStats
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayInfo
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.model.HabitWithStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object HabitRepository {

    private const val PREFS_NAME = "kairo_habits_prefs"
    private const val KEY_HABITS = "saved_habits"
    private const val KEY_LOGS = "saved_habit_logs"

    private val prefs: SharedPreferences by lazy {
        KairoApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _habitsList = MutableStateFlow<List<Habit>>(emptyList())
    // Map of habitId -> Map of "YYYY-MM-DD" -> HabitDayState
    private val _logsMap = MutableStateFlow<Map<String, Map<String, HabitDayState>>>(emptyMap())

    private val _habitsWithStats = MutableStateFlow<List<HabitWithStats>>(emptyList())
    val habitsWithStats: StateFlow<List<HabitWithStats>> = _habitsWithStats.asStateFlow()

    private val _disciplineOverview = MutableStateFlow(
        DisciplineOverviewStats(
            completedToday = 0,
            totalToday = 0,
            percentage = 0,
            remainingToday = 0,
            streakDays = 14
        )
    )
    val disciplineOverview: StateFlow<DisciplineOverviewStats> = _disciplineOverview.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val habitsJson = prefs.getString(KEY_HABITS, null)
        val logsJson = prefs.getString(KEY_LOGS, null)

        val loadedHabits = mutableListOf<Habit>()
        if (!habitsJson.isNullOrBlank()) {
            try {
                val array = JSONArray(habitsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedHabits.add(
                        Habit(
                            id = obj.getString("id"),
                            userCode = obj.optString("userCode", AuthManager.getUserCode() ?: "DEFAULT"),
                            title = obj.getString("title"),
                            type = HabitType.valueOf(obj.optString("type", HabitType.YES_NO.name)),
                            targetValue = obj.optInt("targetValue", 1),
                            targetUnit = obj.optString("targetUnit", ""),
                            repeatType = HabitRepeatType.valueOf(obj.optString("repeatType", HabitRepeatType.EVERYDAY.name)),
                            repeatDays = parseDays(obj.optString("repeatDays", "1,2,3,4,5,6,7")),
                            weeklyTargetCount = obj.optInt("weeklyTargetCount", 3),
                            reminderEnabled = obj.optBoolean("reminderEnabled", false),
                            reminderTime = obj.optString("reminderTime").takeIf { it.isNotBlank() },
                            reminderToneUri = obj.optString("reminderToneUri").takeIf { it.isNotBlank() },
                            reminderToneTitle = obj.optString("reminderToneTitle").takeIf { it.isNotBlank() },
                            position = obj.optInt("position", i),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If no habits saved yet, seed initial reference habits
        if (loadedHabits.isEmpty()) {
            loadedHabits.addAll(getInitialSeedHabits())
        }

        val loadedLogs = mutableMapOf<String, MutableMap<String, HabitDayState>>()
        if (!logsJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(logsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val hId = keys.next()
                    val datesObj = obj.getJSONObject(hId)
                    val dateMap = mutableMapOf<String, HabitDayState>()
                    val dateKeys = datesObj.keys()
                    while (dateKeys.hasNext()) {
                        val d = dateKeys.next()
                        dateMap[d] = HabitDayState.valueOf(datesObj.getString(d))
                    }
                    loadedLogs[hId] = dateMap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Seed sample logs corresponding to reference HTML
            loadedLogs.putAll(getInitialSeedLogs(loadedHabits))
        }

        _habitsList.value = loadedHabits
        _logsMap.value = loadedLogs
        recomputeAllStats()
    }

    private fun persistData() {
        try {
            val habitsArr = JSONArray()
            _habitsList.value.forEach { h ->
                val obj = JSONObject().apply {
                    put("id", h.id)
                    put("userCode", h.userCode)
                    put("title", h.title)
                    put("type", h.type.name)
                    put("targetValue", h.targetValue)
                    put("targetUnit", h.targetUnit)
                    put("repeatType", h.repeatType.name)
                    put("repeatDays", h.repeatDays.joinToString(","))
                    put("weeklyTargetCount", h.weeklyTargetCount)
                    put("reminderEnabled", h.reminderEnabled)
                    put("reminderTime", h.reminderTime ?: "")
                    put("reminderToneUri", h.reminderToneUri ?: "")
                    put("reminderToneTitle", h.reminderToneTitle ?: "")
                    put("position", h.position)
                    put("createdAt", h.createdAt)
                    put("updatedAt", h.updatedAt)
                }
                habitsArr.put(obj)
            }

            val logsObj = JSONObject()
            _logsMap.value.forEach { (hId, dateMap) ->
                val dObj = JSONObject()
                dateMap.forEach { (dateStr, state) ->
                    dObj.put(dateStr, state.name)
                }
                logsObj.put(hId, dObj)
            }

            prefs.edit()
                .putString(KEY_HABITS, habitsArr.toString())
                .putString(KEY_LOGS, logsObj.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addHabit(
        title: String,
        type: HabitType = HabitType.YES_NO,
        targetValue: Int = 1,
        targetUnit: String = "",
        repeatType: HabitRepeatType = HabitRepeatType.EVERYDAY,
        repeatDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
        weeklyTargetCount: Int = 3,
        reminderEnabled: Boolean = false,
        reminderTime: String? = null,
        reminderToneUri: String? = null,
        reminderToneTitle: String? = null
    ) {
        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        val newHabit = Habit(
            id = UUID.randomUUID().toString(),
            userCode = userCode,
            title = title.trim(),
            type = type,
            targetValue = if (type == HabitType.TARGET) targetValue else 1,
            targetUnit = if (type == HabitType.TARGET) targetUnit.trim() else "",
            repeatType = repeatType,
            repeatDays = repeatDays,
            weeklyTargetCount = weeklyTargetCount,
            reminderEnabled = reminderEnabled,
            reminderTime = if (reminderEnabled) reminderTime else null,
            reminderToneUri = if (reminderEnabled) reminderToneUri else null,
            reminderToneTitle = if (reminderEnabled) reminderToneTitle else null,
            position = _habitsList.value.size
        )

        val updated = _habitsList.value.toMutableList().apply { add(newHabit) }
        _habitsList.value = updated
        persistData()
        recomputeAllStats()
    }

    fun updateHabit(updatedHabit: Habit) {
        val list = _habitsList.value.map { if (it.id == updatedHabit.id) updatedHabit else it }
        _habitsList.value = list
        persistData()
        recomputeAllStats()
    }

    fun deleteHabit(habitId: String) {
        val list = _habitsList.value.filter { it.id != habitId }
        val logs = _logsMap.value.toMutableMap().apply { remove(habitId) }
        _habitsList.value = list
        _logsMap.value = logs
        persistData()
        recomputeAllStats()
    }

    fun reorderHabits(fromIndex: Int, toIndex: Int) {
        val list = _habitsList.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            val reindexed = list.mapIndexed { idx, h -> h.copy(position = idx) }
            _habitsList.value = reindexed
            persistData()
            recomputeAllStats()
        }
    }

    /**
     * Cycles day state: UNMARKED -> DONE -> MISSED -> UNMARKED
     */
    fun toggleHabitDay(habitId: String, date: LocalDate) {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentState = habitDates[dateStr] ?: HabitDayState.UNMARKED
        val nextState = when (currentState) {
            HabitDayState.UNMARKED -> HabitDayState.DONE
            HabitDayState.DONE -> HabitDayState.MISSED
            HabitDayState.MISSED -> HabitDayState.UNMARKED
        }

        habitDates[dateStr] = nextState
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()
    }

    private fun recomputeAllStats() {
        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // 14 days window (from today - 13 days to today)
        val dayWindow = (13 downTo 0).map { offset -> today.minusDays(offset.toLong()) }

        var completedTodayCount = 0
        val totalTodayCount = _habitsList.value.size

        val statsList = _habitsList.value.map { habit ->
            val logsForHabit = _logsMap.value[habit.id] ?: emptyMap()

            // Build 14-day info
            val dayInfos = dayWindow.map { d ->
                val dStr = d.format(dateFormatter)
                val state = logsForHabit[dStr] ?: HabitDayState.UNMARKED
                HabitDayInfo(
                    date = d,
                    dayNumber = d.dayOfMonth,
                    dayLabel = formatDayLabel(d.dayOfWeek),
                    isToday = d == today,
                    state = state
                )
            }

            if (logsForHabit[today.format(dateFormatter)] == HabitDayState.DONE) {
                completedTodayCount++
            }

            // Streak computation (counting backward from today or yesterday)
            var currentStreak = 0
            var checkDate = if (logsForHabit[today.format(dateFormatter)] == HabitDayState.DONE) today else today.minusDays(1)
            while (true) {
                val state = logsForHabit[checkDate.format(dateFormatter)]
                if (state == HabitDayState.DONE) {
                    currentStreak++
                    checkDate = checkDate.minusDays(1)
                } else {
                    break
                }
            }

            val totalDone = logsForHabit.values.count { it == HabitDayState.DONE }
            val totalMissed = logsForHabit.values.count { it == HabitDayState.MISSED }
            val totalLogged = totalDone + totalMissed
            val adherence = if (totalLogged > 0) (totalDone.toFloat() / totalLogged * 100f) else 100f

            val bestStreak = maxOf(currentStreak, (totalDone / 2).coerceAtLeast(currentStreak))

            HabitWithStats(
                habit = habit,
                streak = currentStreak,
                bestStreak = bestStreak,
                doneCount = totalDone,
                missedCount = totalMissed,
                adherenceRate = adherence,
                days = dayInfos
            )
        }

        _habitsWithStats.value = statsList

        val pct = if (totalTodayCount > 0) ((completedTodayCount.toFloat() / totalTodayCount) * 100).toInt() else 0
        _disciplineOverview.value = DisciplineOverviewStats(
            completedToday = completedTodayCount,
            totalToday = totalTodayCount,
            percentage = pct,
            remainingToday = (totalTodayCount - completedTodayCount).coerceAtLeast(0),
            streakDays = statsList.maxOfOrNull { it.streak } ?: 0
        )
    }

    private fun formatDayLabel(dow: DayOfWeek): String {
        return when (dow) {
            DayOfWeek.MONDAY -> "M"
            DayOfWeek.TUESDAY -> "Tu"
            DayOfWeek.WEDNESDAY -> "W"
            DayOfWeek.THURSDAY -> "Th"
            DayOfWeek.FRIDAY -> "F"
            DayOfWeek.SATURDAY -> "Sa"
            DayOfWeek.SUNDAY -> "Su"
        }
    }

    private fun parseDays(str: String): List<Int> {
        return str.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun getInitialSeedHabits(): List<Habit> {
        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        return listOf(
            Habit(
                id = "1",
                userCode = userCode,
                title = "Drink 750ml Water Daily",
                type = HabitType.TARGET,
                targetValue = 4,
                targetUnit = "times",
                repeatType = HabitRepeatType.EVERYDAY,
                position = 0
            ),
            Habit(
                id = "2",
                userCode = userCode,
                title = "Morning Meditation",
                type = HabitType.YES_NO,
                repeatType = HabitRepeatType.EVERYDAY,
                position = 1
            ),
            Habit(
                id = "3",
                userCode = userCode,
                title = "Morning Workout / Run",
                type = HabitType.YES_NO,
                repeatType = HabitRepeatType.SPECIFIC_DAYS,
                repeatDays = listOf(1, 3, 5, 6),
                position = 2
            ),
            Habit(
                id = "4",
                userCode = userCode,
                title = "Read 20 pages of Book",
                type = HabitType.TARGET,
                targetValue = 20,
                targetUnit = "pages",
                repeatType = HabitRepeatType.EVERYDAY,
                position = 3
            ),
            Habit(
                id = "5",
                userCode = userCode,
                title = "No Social Media past 9 PM",
                type = HabitType.YES_NO,
                repeatType = HabitRepeatType.EVERYDAY,
                position = 4
            ),
            Habit(
                id = "6",
                userCode = userCode,
                title = "Daily Gratitude Journal",
                type = HabitType.YES_NO,
                repeatType = HabitRepeatType.EVERYDAY,
                position = 5
            )
        )
    }

    private fun getInitialSeedLogs(habits: List<Habit>): Map<String, MutableMap<String, HabitDayState>> {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val result = mutableMapOf<String, MutableMap<String, HabitDayState>>()

        habits.forEachIndexed { idx, h ->
            val map = mutableMapOf<String, HabitDayState>()
            for (offset in 0..13) {
                val d = today.minusDays(offset.toLong())
                val dStr = d.format(fmt)
                // Seed some variations like the reference HTML
                val state = when {
                    offset == 0 -> if (idx < 4) HabitDayState.DONE else HabitDayState.UNMARKED
                    offset == 1 -> if (idx == 2 || idx == 5) HabitDayState.MISSED else HabitDayState.DONE
                    offset == 5 -> if (idx == 3) HabitDayState.MISSED else HabitDayState.DONE
                    offset % 4 == 0 -> HabitDayState.MISSED
                    else -> HabitDayState.DONE
                }
                map[dStr] = state
            }
            result[h.id] = map
        }
        return result
    }
}
