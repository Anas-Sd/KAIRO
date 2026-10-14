package com.kairo.app.feature.habits.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kairo.app.KairoApplication
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.feature.habits.model.DisciplineOverviewStats
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayInfo
import com.kairo.app.feature.habits.model.HabitDayRecord
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitNoteHelper
import com.kairo.app.feature.habits.model.HabitNotePrompt
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.feature.sync.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object HabitRepository {

    private const val TAG = "HabitRepository"
    private const val PREFS_NAME = "kairo_habits_prefs"
    private const val KEY_HABITS = "saved_habits"
    private const val KEY_LOGS = "saved_habit_logs"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences by lazy {
        KairoApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _habitsList = MutableStateFlow<List<Habit>>(emptyList())
    val habitsList: StateFlow<List<Habit>> = _habitsList.asStateFlow()
    // Map of habitId -> Map of "YYYY-MM-DD" -> HabitDayRecord
    private val _logsMap = MutableStateFlow<Map<String, Map<String, HabitDayRecord>>>(emptyMap())
    val logsMap: StateFlow<Map<String, Map<String, HabitDayRecord>>> = _logsMap.asStateFlow()

    private val _habitsWithStats = MutableStateFlow<List<HabitWithStats>>(emptyList())
    val habitsWithStats: StateFlow<List<HabitWithStats>> = _habitsWithStats.asStateFlow()

    private val _disciplineOverview = MutableStateFlow(
        DisciplineOverviewStats(
            completedToday = 0,
            totalToday = 0,
            percentage = 0,
            remainingToday = 0,
            streakDays = 0
        )
    )
    val disciplineOverview: StateFlow<DisciplineOverviewStats> = _disciplineOverview.asStateFlow()

    init {
        loadDataFromLocal()
        // Pull latest from Supabase on init
        fetchFromSupabase()
    }

    private fun loadDataFromLocal() {
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
                            notePrompt = runCatching {
                                HabitNotePrompt.valueOf(obj.optString("notePrompt", HabitNotePrompt.BOTH.name))
                            }.getOrDefault(HabitNotePrompt.BOTH),
                            startDate = obj.optString("startDate", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val loadedLogs = mutableMapOf<String, MutableMap<String, HabitDayRecord>>()
        if (!logsJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(logsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val hId = keys.next()
                    val datesObj = obj.getJSONObject(hId)
                    val dateMap = mutableMapOf<String, HabitDayRecord>()
                    val dateKeys = datesObj.keys()
                    while (dateKeys.hasNext()) {
                        val d = dateKeys.next()
                        val raw = datesObj.get(d)
                        if (raw is JSONObject) {
                            val state = HabitDayState.valueOf(raw.optString("state", HabitDayState.DONE.name))
                            val value = raw.optInt("value", if (state == HabitDayState.DONE) 1 else 0)
                            val note = raw.optString("note", "").takeIf { it.isNotBlank() }
                            dateMap[d] = HabitDayRecord(state = state, value = value, note = note)
                        } else {
                            val state = HabitDayState.valueOf(raw.toString())
                            dateMap[d] = HabitDayRecord(state = state, value = if (state == HabitDayState.DONE) 1 else 0)
                        }
                    }
                    loadedLogs[hId] = dateMap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _habitsList.value = loadedHabits
        _logsMap.value = loadedLogs
        autoMarkMissedPastDays()
        recomputeAllStats()
    }

    fun getEffectiveStartDate(habit: Habit): LocalDate {
        val parsed = runCatching {
            if (habit.startDate.isNotBlank()) LocalDate.parse(habit.startDate, DateTimeFormatter.ISO_LOCAL_DATE) else null
        }.getOrNull()
        if (parsed != null) return parsed
        return runCatching {
            java.time.Instant.ofEpochMilli(habit.createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }.getOrDefault(LocalDate.now())
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
                    put("notePrompt", h.notePrompt.name)
                    put("startDate", h.startDate)
                    put("createdAt", h.createdAt)
                    put("updatedAt", h.updatedAt)
                }
                habitsArr.put(obj)
            }

            val logsObj = JSONObject()
            _logsMap.value.forEach { (hId, dateMap) ->
                val dObj = JSONObject()
                dateMap.forEach { (dateStr, record) ->
                    val recObj = JSONObject().apply {
                        put("state", record.state.name)
                        put("value", record.value)
                        if (!record.note.isNullOrBlank()) {
                            put("note", record.note)
                        }
                    }
                    dObj.put(dateStr, recObj)
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

    fun fetchFromSupabase() {
        scope.launch {
            val userCode = AuthManager.getUserCode()
            val habitsResult = SupabaseClient.getHabits(userCode)
            val logsResult = SupabaseClient.getHabitLogs(userCode)

            if (habitsResult.isSuccess) {
                val dtoList = habitsResult.getOrNull().orEmpty()
                val habits = dtoList.map { dto ->
                    Habit(
                        id = dto.id,
                        userCode = dto.userCode,
                        title = dto.title,
                        type = runCatching { HabitType.valueOf(dto.type) }.getOrDefault(HabitType.YES_NO),
                        targetValue = dto.targetValue,
                        targetUnit = dto.targetUnit,
                        repeatType = runCatching { HabitRepeatType.valueOf(dto.repeatType) }.getOrDefault(HabitRepeatType.EVERYDAY),
                        repeatDays = parseDays(dto.repeatDays),
                        weeklyTargetCount = dto.weeklyTargetCount,
                        reminderEnabled = dto.reminderEnabled,
                        reminderTime = dto.reminderTime,
                        reminderToneUri = dto.reminderToneUri,
                        reminderToneTitle = dto.reminderToneTitle,
                        position = dto.position,
                        notePrompt = runCatching { HabitNotePrompt.valueOf(dto.notePrompt) }.getOrDefault(HabitNotePrompt.BOTH),
                        startDate = dto.startDate,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt
                    )
                }.sortedBy { it.position }

                _habitsList.value = habits
            } else {
                Log.w(TAG, "Could not fetch habits from Supabase: ${habitsResult.exceptionOrNull()?.message}")
            }

            if (logsResult.isSuccess) {
                val dtoList = logsResult.getOrNull().orEmpty()
                val logs = mutableMapOf<String, MutableMap<String, HabitDayRecord>>()
                dtoList.forEach { dto ->
                    val habitMap = logs.getOrPut(dto.habitId) { mutableMapOf() }
                    val state = runCatching { HabitDayState.valueOf(dto.state) }.getOrDefault(HabitDayState.DONE)
                    val value = dto.value ?: if (state == HabitDayState.DONE) 1 else 0
                    val note = dto.note?.takeIf { it.isNotBlank() }
                    habitMap[dto.date] = HabitDayRecord(state = state, value = value, note = note)
                }
                _logsMap.value = logs
            } else {
                Log.w(TAG, "Could not fetch habit logs from Supabase: ${logsResult.exceptionOrNull()?.message}")
            }

            persistData()
            autoMarkMissedPastDays()
            recomputeAllStats()
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
        reminderToneTitle: String? = null,
        notePrompt: HabitNotePrompt = HabitNotePrompt.BOTH,
        startDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
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
            position = _habitsList.value.size,
            notePrompt = notePrompt,
            startDate = startDate.ifBlank { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
        )

        val updated = _habitsList.value.toMutableList().apply { add(newHabit) }
        _habitsList.value = updated
        persistData()
        autoMarkMissedPastDays()
        recomputeAllStats()

        // Sync to Supabase
        scope.launch {
            val dto = HabitDto(
                id = newHabit.id,
                userCode = newHabit.userCode,
                title = newHabit.title,
                type = newHabit.type.name,
                targetValue = newHabit.targetValue,
                targetUnit = newHabit.targetUnit,
                repeatType = newHabit.repeatType.name,
                repeatDays = newHabit.repeatDays.joinToString(","),
                weeklyTargetCount = newHabit.weeklyTargetCount,
                reminderEnabled = newHabit.reminderEnabled,
                reminderTime = newHabit.reminderTime,
                reminderToneUri = newHabit.reminderToneUri,
                reminderToneTitle = newHabit.reminderToneTitle,
                position = newHabit.position,
                notePrompt = newHabit.notePrompt.name,
                startDate = newHabit.startDate,
                createdAt = newHabit.createdAt,
                updatedAt = newHabit.updatedAt
            )
            SupabaseClient.upsertHabit(dto)
        }
    }

    fun updateHabit(updatedHabit: Habit) {
        val list = _habitsList.value.map { if (it.id == updatedHabit.id) updatedHabit else it }
        _habitsList.value = list
        persistData()
        recomputeAllStats()

        scope.launch {
            val dto = HabitDto(
                id = updatedHabit.id,
                userCode = updatedHabit.userCode,
                title = updatedHabit.title,
                type = updatedHabit.type.name,
                targetValue = updatedHabit.targetValue,
                targetUnit = updatedHabit.targetUnit,
                repeatType = updatedHabit.repeatType.name,
                repeatDays = updatedHabit.repeatDays.joinToString(","),
                weeklyTargetCount = updatedHabit.weeklyTargetCount,
                reminderEnabled = updatedHabit.reminderEnabled,
                reminderTime = updatedHabit.reminderTime,
                reminderToneUri = updatedHabit.reminderToneUri,
                reminderToneTitle = updatedHabit.reminderToneTitle,
                position = updatedHabit.position,
                notePrompt = updatedHabit.notePrompt.name,
                startDate = updatedHabit.startDate,
                createdAt = updatedHabit.createdAt,
                updatedAt = updatedHabit.updatedAt
            )
            SupabaseClient.upsertHabit(dto)
        }
    }

    fun deleteHabit(habitId: String) {
        val list = _habitsList.value.filter { it.id != habitId }
        val logs = _logsMap.value.toMutableMap().apply { remove(habitId) }
        _habitsList.value = list
        _logsMap.value = logs
        persistData()
        recomputeAllStats()

        scope.launch {
            SupabaseClient.deleteHabitLogsForHabit(habitId)
            SupabaseClient.deleteHabit(habitId)
        }
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

            scope.launch {
                reindexed.forEach { h ->
                    val dto = HabitDto(
                        id = h.id,
                        userCode = h.userCode,
                        title = h.title,
                        type = h.type.name,
                        targetValue = h.targetValue,
                        targetUnit = h.targetUnit,
                        repeatType = h.repeatType.name,
                        repeatDays = h.repeatDays.joinToString(","),
                        weeklyTargetCount = h.weeklyTargetCount,
                        reminderEnabled = h.reminderEnabled,
                        reminderTime = h.reminderTime,
                        reminderToneUri = h.reminderToneUri,
                        reminderToneTitle = h.reminderToneTitle,
                        position = h.position,
                        createdAt = h.createdAt,
                        updatedAt = h.updatedAt
                    )
                    SupabaseClient.upsertHabit(dto)
                }
            }
        }
    }

    fun reorderHabitsByIds(orderedIds: List<String>) {
        val current = _habitsList.value
        val map = current.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { map[it] }
        val finalOrder = if (reordered.size == current.size) reordered else {
            val missing = current.filter { !orderedIds.contains(it.id) }
            reordered + missing
        }
        val reindexed = finalOrder.mapIndexed { idx, h -> h.copy(position = idx) }
        _habitsList.value = reindexed
        persistData()
        recomputeAllStats()

        scope.launch {
            reindexed.forEach { h ->
                val dto = HabitDto(
                    id = h.id,
                    userCode = h.userCode,
                    title = h.title,
                    type = h.type.name,
                    targetValue = h.targetValue,
                    targetUnit = h.targetUnit,
                    repeatType = h.repeatType.name,
                    repeatDays = h.repeatDays.joinToString(","),
                    weeklyTargetCount = h.weeklyTargetCount,
                    reminderEnabled = h.reminderEnabled,
                    reminderTime = h.reminderTime,
                    reminderToneUri = h.reminderToneUri,
                    reminderToneTitle = h.reminderToneTitle,
                    position = h.position,
                    createdAt = h.createdAt,
                    updatedAt = h.updatedAt
                )
                SupabaseClient.upsertHabit(dto)
            }
        }
    }

    fun completeHabitsToday(habitIds: Set<String>) {
        val today = LocalDate.now()
        val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val userCode = AuthManager.getUserCode() ?: "DEFAULT"

        habitIds.forEach { hId ->
            val habit = _habitsList.value.find { it.id == hId }
            val target = habit?.targetValue ?: 1
            val habitDates = currentLogs[hId]?.toMutableMap() ?: mutableMapOf()
            habitDates[dateStr] = HabitDayRecord(HabitDayState.DONE, target)
            currentLogs[hId] = habitDates

            scope.launch {
                val logDto = HabitLogDto(
                    id = "${hId}_${dateStr}",
                    habitId = hId,
                    userCode = userCode,
                    date = dateStr,
                    state = HabitDayState.DONE.name,
                    value = target,
                    createdAt = System.currentTimeMillis()
                )
                SupabaseClient.upsertHabitLog(logDto)
            }
        }

        _logsMap.value = currentLogs
        persistData()
        recomputeAllStats()
    }

    fun deleteHabits(habitIds: Set<String>) {
        if (habitIds.isEmpty()) return
        val list = _habitsList.value.filter { !habitIds.contains(it.id) }
        val logs = _logsMap.value.toMutableMap().apply {
            habitIds.forEach { remove(it) }
        }
        _habitsList.value = list
        _logsMap.value = logs
        persistData()
        recomputeAllStats()

        scope.launch {
            habitIds.forEach { id ->
                SupabaseClient.deleteHabitLogsForHabit(id)
                SupabaseClient.deleteHabit(id)
            }
        }
    }

    fun getHabitLogs(habitId: String): Map<String, HabitDayRecord> {
        return _logsMap.value[habitId] ?: emptyMap()
    }

    /**
     * Increments or decrements today's (or given date's) progress value for a Target habit.
     * Notes are stored in a 1-indexed hashmap per count so removing count N only removes note N.
     */
    fun incrementHabitDay(habitId: String, date: LocalDate, delta: Int = 1, note: String? = null) {
        if (date.isAfter(LocalDate.now())) return
        val habit = _habitsList.value.find { it.id == habitId } ?: return
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentRecord = habitDates[dateStr] ?: HabitDayRecord()
        val target = habit.targetValue.coerceAtLeast(1)
        val newValue = (currentRecord.value + delta).coerceIn(0, target)
        val isPastDate = date.isBefore(LocalDate.now())

        val newState = when {
            newValue >= target -> HabitDayState.DONE
            isPastDate -> HabitDayState.MISSED
            newValue == 0 -> HabitDayState.UNMARKED
            else -> HabitDayState.UNMARKED
        }

        val finalNote = when {
            note != null -> {
                val clean = note.trim()
                if (clean.isBlank()) {
                    currentRecord.note
                } else {
                    val map = HabitNoteHelper.parseIncrementNotes(currentRecord.note).toMutableMap()
                    map[newValue] = clean
                    HabitNoteHelper.serializeIncrementNotes(map)
                }
            }
            delta < 0 -> {
                if (newValue == 0) {
                    null
                } else {
                    // Exact hashmap removal: remove the decremented count index (currentRecord.value)
                    val map = HabitNoteHelper.parseIncrementNotes(currentRecord.note).toMutableMap()
                    map.remove(currentRecord.value)
                    val trimmed = map.filterKeys { it <= newValue }
                    HabitNoteHelper.serializeIncrementNotes(trimmed)
                }
            }
            else -> currentRecord.note
        }

        if (newValue == 0 && newState == HabitDayState.UNMARKED && finalNote == null) {
            habitDates.remove(dateStr)
        } else {
            habitDates[dateStr] = HabitDayRecord(state = newState, value = newValue, note = finalNote)
        }
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()

        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        scope.launch {
            if (newValue == 0 && newState == HabitDayState.UNMARKED && finalNote == null) {
                SupabaseClient.deleteHabitLog(habitId, dateStr)
            } else {
                val logDto = HabitLogDto(
                    id = "${habitId}_${dateStr}",
                    habitId = habitId,
                    userCode = userCode,
                    date = dateStr,
                    state = newState.name,
                    value = newValue,
                    note = finalNote,
                    createdAt = System.currentTimeMillis()
                )
                SupabaseClient.upsertHabitLog(logDto)
            }
        }
    }

    /**
     * Sets specific value for a target habit on a given date.
     */
    fun setHabitDayValue(
        habitId: String,
        date: LocalDate,
        value: Int,
        note: String? = null,
        state: HabitDayState? = null
    ) {
        if (date.isAfter(LocalDate.now())) return
        val habit = _habitsList.value.find { it.id == habitId } ?: return
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentRecord = habitDates[dateStr] ?: HabitDayRecord()
        val target = habit.targetValue.coerceAtLeast(1)
        val cleanVal = value.coerceIn(0, target)
        val isPastDate = date.isBefore(LocalDate.now())

        val newState = when {
            cleanVal >= target -> HabitDayState.DONE
            isPastDate -> HabitDayState.MISSED
            state != null -> state
            cleanVal == 0 -> HabitDayState.UNMARKED
            else -> HabitDayState.UNMARKED
        }

        val finalNote = when {
            note != null -> note.trim().takeIf { it.isNotBlank() }
            isPastDate && cleanVal == 0 && currentRecord.note.isNullOrBlank() -> "missed"
            cleanVal == 0 && !isPastDate -> null
            cleanVal < currentRecord.value && !currentRecord.note.isNullOrBlank() -> {
                val map = HabitNoteHelper.parseIncrementNotes(currentRecord.note).filterKeys { it <= cleanVal }
                HabitNoteHelper.serializeIncrementNotes(map)
            }
            else -> currentRecord.note
        }

        if (cleanVal == 0 && newState == HabitDayState.UNMARKED && finalNote == null) {
            habitDates.remove(dateStr)
        } else {
            habitDates[dateStr] = HabitDayRecord(state = newState, value = cleanVal, note = finalNote)
        }
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()

        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        scope.launch {
            if (cleanVal == 0 && newState == HabitDayState.UNMARKED && finalNote == null) {
                SupabaseClient.deleteHabitLog(habitId, dateStr)
            } else {
                val logDto = HabitLogDto(
                    id = "${habitId}_${dateStr}",
                    habitId = habitId,
                    userCode = userCode,
                    date = dateStr,
                    state = newState.name,
                    value = cleanVal,
                    note = finalNote,
                    createdAt = System.currentTimeMillis()
                )
                SupabaseClient.upsertHabitLog(logDto)
            }
        }
    }

    /**
     * Sets or updates note for a specific count index (1-indexed) in the habit day's hashmap.
     */
    fun setIncrementNote(habitId: String, date: LocalDate, countIndex: Int, note: String?) {
        if (date.isAfter(LocalDate.now())) return
        val habit = _habitsList.value.find { it.id == habitId } ?: return
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentRecord = habitDates[dateStr] ?: HabitDayRecord(state = HabitDayState.UNMARKED, value = countIndex)
        val map = HabitNoteHelper.parseIncrementNotes(currentRecord.note).toMutableMap()
        val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }

        if (cleanNote == null) {
            map.remove(countIndex)
        } else {
            map[countIndex] = cleanNote
        }

        val finalNote = HabitNoteHelper.serializeIncrementNotes(map)
        val updatedRecord = currentRecord.copy(note = finalNote)
        habitDates[dateStr] = updatedRecord
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()

        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        scope.launch {
            val logDto = HabitLogDto(
                id = "${habitId}_${dateStr}",
                habitId = habitId,
                userCode = userCode,
                date = dateStr,
                state = updatedRecord.state.name,
                value = updatedRecord.value,
                note = finalNote,
                createdAt = System.currentTimeMillis()
            )
            SupabaseClient.upsertHabitLog(logDto)
        }
    }

    fun setHabitDayState(
        habitId: String,
        date: LocalDate,
        state: HabitDayState,
        note: String? = null,
        clearNote: Boolean = false
    ) {
        if (date.isAfter(LocalDate.now())) return
        val habit = _habitsList.value.find { it.id == habitId }
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentRecord = habitDates[dateStr]
        val isPastDate = date.isBefore(LocalDate.now())
        val effectiveState = if (isPastDate && state == HabitDayState.UNMARKED) HabitDayState.MISSED else state
        val finalNote = when {
            clearNote -> null
            note?.trim()?.isNotBlank() == true -> note.trim()
            effectiveState == HabitDayState.MISSED && currentRecord?.note.isNullOrBlank() -> "missed"
            else -> currentRecord?.note
        }

        val targetVal = habit?.targetValue ?: 1
        val assignedValue = when (effectiveState) {
            HabitDayState.DONE -> targetVal
            HabitDayState.MISSED -> currentRecord?.value ?: 0
            HabitDayState.UNMARKED -> 0
        }

        if (effectiveState == HabitDayState.UNMARKED && finalNote == null) {
            habitDates.remove(dateStr)
        } else {
            habitDates[dateStr] = HabitDayRecord(state = effectiveState, value = assignedValue, note = finalNote)
        }
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()

        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        scope.launch {
            if (state == HabitDayState.UNMARKED && finalNote == null) {
                SupabaseClient.deleteHabitLog(habitId, dateStr)
            } else {
                val logDto = HabitLogDto(
                    id = "${habitId}_${dateStr}",
                    habitId = habitId,
                    userCode = userCode,
                    date = dateStr,
                    state = state.name,
                    value = assignedValue,
                    note = finalNote,
                    createdAt = System.currentTimeMillis()
                )
                SupabaseClient.upsertHabitLog(logDto)
            }
        }
    }

    fun setHabitDayNote(habitId: String, date: LocalDate, note: String?) {
        if (date.isAfter(LocalDate.now())) return
        val habit = _habitsList.value.find { it.id == habitId } ?: return
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentRecord = habitDates[dateStr] ?: HabitDayRecord(state = HabitDayState.DONE, value = habit.targetValue)
        val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }
        val updatedRecord = currentRecord.copy(note = cleanNote)
        habitDates[dateStr] = updatedRecord
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()

        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        scope.launch {
            val logDto = HabitLogDto(
                id = "${habitId}_${dateStr}",
                habitId = habitId,
                userCode = userCode,
                date = dateStr,
                state = updatedRecord.state.name,
                value = updatedRecord.value,
                note = cleanNote,
                createdAt = System.currentTimeMillis()
            )
            SupabaseClient.upsertHabitLog(logDto)
        }
    }

    /**
     * Appends an increment note to the existing note on a given day (comma-separated).
     */
    fun appendHabitDayNote(habitId: String, date: LocalDate, note: String?) {
        if (date.isAfter(LocalDate.now())) return
        val cleanNote = note?.trim()?.takeIf { it.isNotBlank() } ?: return
        val habit = _habitsList.value.find { it.id == habitId } ?: return
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentRecord = habitDates[dateStr] ?: HabitDayRecord(state = HabitDayState.UNMARKED, value = 0)
        val existingNotes = currentRecord.note
            ?.split(",", "\n", ";")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val combinedNote = if (existingNotes.isEmpty()) {
            cleanNote
        } else {
            (existingNotes + cleanNote).joinToString(", ")
        }

        val updatedRecord = currentRecord.copy(note = combinedNote)
        habitDates[dateStr] = updatedRecord
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()

        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        scope.launch {
            val logDto = HabitLogDto(
                id = "${habitId}_${dateStr}",
                habitId = habitId,
                userCode = userCode,
                date = dateStr,
                state = updatedRecord.state.name,
                value = updatedRecord.value,
                note = combinedNote,
                createdAt = System.currentTimeMillis()
            )
            SupabaseClient.upsertHabitLog(logDto)
        }
    }

    /**
     * Automatically scans past days (up to yesterday). If a habit was scheduled for a past day
     * and was NOT completed, it is automatically marked MISSED with note "missed".
     */
    fun autoMarkMissedPastDays() {
        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        var anyChanged = false
        val currentLogs = _logsMap.value.toMutableMap()
        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        val logsToUpsert = mutableListOf<HabitLogDto>()
        _habitsList.value.forEach { habit ->
            val habitStartDate = getEffectiveStartDate(habit)

            // Only auto-mark missed from the habit start date up to yesterday
            val startDate = maxOf(today.minusDays(60), habitStartDate)
            if (!startDate.isAfter(today.minusDays(1))) {
                val habitDates = currentLogs[habit.id]?.toMutableMap() ?: mutableMapOf()
                var checkDate = startDate

                while (!checkDate.isAfter(today.minusDays(1))) {
                    val isScheduled = when (habit.repeatType) {
                        HabitRepeatType.EVERYDAY -> true
                        HabitRepeatType.SPECIFIC_DAYS -> habit.repeatDays.contains(checkDate.dayOfWeek.value)
                        HabitRepeatType.WEEKLY_X -> false
                    }

                    if (isScheduled) {
                        val dateStr = checkDate.format(dateFormatter)
                        val currentRec = habitDates[dateStr]
                        val isDone = currentRec?.state == HabitDayState.DONE ||
                                (habit.type == HabitType.TARGET && (currentRec?.value ?: 0) >= habit.targetValue && habit.targetValue > 0)

                        if (!isDone && currentRec?.state != HabitDayState.MISSED) {
                            val missedRec = HabitDayRecord(
                                state = HabitDayState.MISSED,
                                value = currentRec?.value ?: 0,
                                note = currentRec?.note?.takeIf { it.isNotBlank() } ?: "missed"
                            )
                            habitDates[dateStr] = missedRec
                            anyChanged = true
                            logsToUpsert.add(
                                HabitLogDto(
                                    id = "${habit.id}_${dateStr}",
                                    habitId = habit.id,
                                    userCode = userCode,
                                    date = dateStr,
                                    state = HabitDayState.MISSED.name,
                                    value = missedRec.value,
                                    note = missedRec.note,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    checkDate = checkDate.plusDays(1)
                }

                if (anyChanged) {
                    currentLogs[habit.id] = habitDates
                }
            }
        }

        if (anyChanged) {
            _logsMap.value = currentLogs
            persistData()
            recomputeAllStats()

            if (logsToUpsert.isNotEmpty()) {
                scope.launch {
                    logsToUpsert.forEach { dto ->
                        SupabaseClient.upsertHabitLog(dto)
                    }
                }
            }
        }
    }

    data class NoteOccurrence(
        val habitId: String,
        val habitTitle: String,
        val date: String,
        val state: HabitDayState,
        val value: Int
    )

    fun getRecentNotes(habitId: String? = null, state: HabitDayState? = null): List<String> {
        val notesWithScore = mutableMapOf<String, Int>()
        _logsMap.value.forEach { (hId, dateMap) ->
            val isCurrentHabit = habitId != null && hId == habitId
            val baseWeight = if (isCurrentHabit) 10 else 1
            dateMap.toSortedMap(compareByDescending { it }).forEach { (_, rec) ->
                val rawNote = rec.note
                if (!rawNote.isNullOrBlank()) {
                    val isIncrementNote = rawNote.trim().startsWith("{")
                    val isDone = rec.state == HabitDayState.DONE || rec.value > 0 || isIncrementNote
                    val matchesState = state == null ||
                            (state == HabitDayState.DONE && isDone) ||
                            (state == HabitDayState.MISSED && rec.state == HabitDayState.MISSED && !isIncrementNote)

                    if (matchesState) {
                        val items = HabitNoteHelper.extractAllNotes(rawNote)
                        items.forEach { noteItem ->
                            val clean = noteItem.trim()
                            if (clean.isNotBlank() && !clean.equals("missed", ignoreCase = true)) {
                                notesWithScore[clean] = (notesWithScore[clean] ?: 0) + baseWeight
                            }
                        }
                    }
                }
            }
        }
        return notesWithScore.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    fun getOccurrencesForNote(noteText: String, stateFilter: HabitDayState? = null): List<NoteOccurrence> {
        val result = mutableListOf<NoteOccurrence>()
        val habitsMap = _habitsList.value.associateBy { it.id }
        _logsMap.value.forEach { (hId, dateMap) ->
            val habitTitle = habitsMap[hId]?.title ?: "Habit"
            dateMap.forEach { (dateStr, rec) ->
                if (!rec.note.isNullOrBlank()) {
                    val items = HabitNoteHelper.extractAllNotes(rec.note)
                    if (items.any { it.equals(noteText.trim(), ignoreCase = true) }) {
                        val isIncrementNote = rec.note.trim().startsWith("{")
                        val effectiveState = if (isIncrementNote || rec.value > 0) HabitDayState.DONE else rec.state
                        val matchesState = stateFilter == null ||
                                (stateFilter == HabitDayState.DONE && effectiveState == HabitDayState.DONE) ||
                                (stateFilter == HabitDayState.MISSED && rec.state == HabitDayState.MISSED && !isIncrementNote)

                        if (matchesState) {
                            result.add(
                                NoteOccurrence(
                                    habitId = hId,
                                    habitTitle = habitTitle,
                                    date = dateStr,
                                    state = effectiveState,
                                    value = rec.value
                                )
                            )
                        }
                    }
                }
            }
        }
        return result.sortedByDescending { it.date }
    }

    /**
     * Cycles day state: UNMARKED -> DONE -> MISSED -> UNMARKED
     */
    fun toggleHabitDay(habitId: String, date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        val habit = _habitsList.value.find { it.id == habitId }
        val target = habit?.targetValue ?: 1
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = _logsMap.value.toMutableMap()
        val habitDates = currentLogs[habitId]?.toMutableMap() ?: mutableMapOf()

        val currentRecord = habitDates[dateStr] ?: HabitDayRecord(HabitDayState.UNMARKED, 0)
        val isPastDate = date.isBefore(LocalDate.now())

        val (nextState, nextValue) = if (isPastDate) {
            // For previous days, there are ONLY 2 options: DONE / MISSED
            if (currentRecord.state == HabitDayState.DONE) {
                HabitDayState.MISSED to 0
            } else {
                HabitDayState.DONE to target
            }
        } else {
            when (currentRecord.state) {
                HabitDayState.UNMARKED -> HabitDayState.DONE to target
                HabitDayState.DONE -> HabitDayState.MISSED to 0
                HabitDayState.MISSED -> HabitDayState.UNMARKED to 0
            }
        }

        if (nextState == HabitDayState.UNMARKED && nextValue == 0) {
            habitDates.remove(dateStr)
        } else {
            habitDates[dateStr] = HabitDayRecord(
                state = nextState,
                value = nextValue,
                note = if (nextState == HabitDayState.MISSED && currentRecord.note.isNullOrBlank()) "missed" else currentRecord.note
            )
        }
        currentLogs[habitId] = habitDates
        _logsMap.value = currentLogs

        persistData()
        recomputeAllStats()

        val userCode = AuthManager.getUserCode() ?: "DEFAULT"
        scope.launch {
            if (nextState == HabitDayState.UNMARKED && nextValue == 0) {
                SupabaseClient.deleteHabitLog(habitId, dateStr)
            } else {
                val logDto = HabitLogDto(
                    id = "${habitId}_${dateStr}",
                    habitId = habitId,
                    userCode = userCode,
                    date = dateStr,
                    state = nextState.name,
                    value = nextValue,
                    createdAt = System.currentTimeMillis()
                )
                SupabaseClient.upsertHabitLog(logDto)
            }
        }
    }

    private fun isDayComplete(habit: Habit, record: HabitDayRecord?): Boolean {
        if (record == null) return false
        if (record.state == HabitDayState.DONE) return true
        if (habit.type == HabitType.TARGET && record.value >= habit.targetValue && habit.targetValue > 0) return true
        return false
    }

    private fun calculateStreak(habit: Habit, logs: Map<String, HabitDayRecord>, today: LocalDate): Int {
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        when (habit.repeatType) {
            HabitRepeatType.EVERYDAY -> {
                val todayDone = isDayComplete(habit, logs[today.format(dateFormatter)])
                var streak = 0
                var checkDate = if (todayDone) today else today.minusDays(1)
                while (true) {
                    val done = isDayComplete(habit, logs[checkDate.format(dateFormatter)])
                    if (done) {
                        streak++
                        checkDate = checkDate.minusDays(1)
                    } else {
                        break
                    }
                }
                return streak
            }

            HabitRepeatType.SPECIFIC_DAYS -> {
                val isTodayScheduled = habit.repeatDays.contains(today.dayOfWeek.value)
                val todayDone = isDayComplete(habit, logs[today.format(dateFormatter)])

                var streak = 0
                var checkDate = if (isTodayScheduled && todayDone) {
                    today
                } else {
                    var prev = today.minusDays(1)
                    var safety = 0
                    while (!habit.repeatDays.contains(prev.dayOfWeek.value) && safety < 14) {
                        prev = prev.minusDays(1)
                        safety++
                    }
                    prev
                }

                var safety = 0
                while (safety < 365) {
                    safety++
                    if (habit.repeatDays.contains(checkDate.dayOfWeek.value)) {
                        val done = isDayComplete(habit, logs[checkDate.format(dateFormatter)])
                        if (done) {
                            streak++
                            checkDate = checkDate.minusDays(1)
                        } else {
                            break
                        }
                    } else {
                        // Rest day -> skip without breaking streak!
                        checkDate = checkDate.minusDays(1)
                    }
                }
                return streak
            }

            HabitRepeatType.WEEKLY_X -> {
                val weeklyTarget = habit.weeklyTargetCount.coerceIn(1, 7)
                val startOfCurrentWeek = today.with(DayOfWeek.MONDAY)
                var currentWeekDone = 0
                var d = startOfCurrentWeek
                while (!d.isAfter(today)) {
                    if (isDayComplete(habit, logs[d.format(dateFormatter)])) {
                        currentWeekDone++
                    }
                    d = d.plusDays(1)
                }

                val currentWeekTargetReached = currentWeekDone >= weeklyTarget
                val daysRemainingInWeek = 7 - today.dayOfWeek.value
                val currentWeekPossible = (currentWeekDone + daysRemainingInWeek) >= weeklyTarget

                if (!currentWeekTargetReached && !currentWeekPossible) {
                    return 0
                }

                var streak = if (currentWeekTargetReached) 1 else 0
                var checkWeekStart = startOfCurrentWeek.minusWeeks(1)
                var weekSafety = 0

                while (weekSafety < 52) {
                    weekSafety++
                    var weekDoneCount = 0
                    for (offset in 0 until 7) {
                        val dayInWeek = checkWeekStart.plusDays(offset.toLong())
                        if (isDayComplete(habit, logs[dayInWeek.format(dateFormatter)])) {
                            weekDoneCount++
                        }
                    }
                    if (weekDoneCount >= weeklyTarget) {
                        streak++
                        checkWeekStart = checkWeekStart.minusWeeks(1)
                    } else {
                        break
                    }
                }
                return streak
            }
        }
    }

    private fun calculateAdherence(habit: Habit, logs: Map<String, HabitDayRecord>, today: LocalDate): Float {
        if (logs.isEmpty()) return 100f
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val habitStartDate = getEffectiveStartDate(habit)

        when (habit.repeatType) {
            HabitRepeatType.EVERYDAY -> {
                val totalDone = logs.entries.count { (dateStr, record) ->
                    val d = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()
                    isDayComplete(habit, record) && (d == null || !d.isBefore(habitStartDate))
                }
                val totalMissed = logs.entries.count { (dateStr, record) ->
                    val d = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()
                    record.state == HabitDayState.MISSED && (d == null || !d.isBefore(habitStartDate))
                }
                val totalLogged = totalDone + totalMissed
                return if (totalLogged > 0) (totalDone.toFloat() / totalLogged * 100f) else 100f
            }
            HabitRepeatType.SPECIFIC_DAYS -> {
                var totalScheduledDone = 0
                var totalScheduledMissed = 0
                logs.forEach { (dateStr, record) ->
                    val d = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()
                    if (d != null && habit.repeatDays.contains(d.dayOfWeek.value) && !d.isBefore(habitStartDate)) {
                        if (isDayComplete(habit, record)) totalScheduledDone++
                        else if (record.state == HabitDayState.MISSED) totalScheduledMissed++
                    }
                }
                val total = totalScheduledDone + totalScheduledMissed
                return if (total > 0) (totalScheduledDone.toFloat() / total * 100f) else 100f
            }
            HabitRepeatType.WEEKLY_X -> {
                val totalDone = logs.entries.count { (dateStr, record) ->
                    val d = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()
                    isDayComplete(habit, record) && (d == null || !d.isBefore(habitStartDate))
                }
                val totalMissed = logs.entries.count { (dateStr, record) ->
                    val d = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()
                    record.state == HabitDayState.MISSED && (d == null || !d.isBefore(habitStartDate))
                }
                val totalLogged = totalDone + totalMissed
                return if (totalLogged > 0) (totalDone.toFloat() / totalLogged * 100f) else 100f
            }
        }
    }

    private fun calculateBestStreak(habit: Habit, logs: Map<String, HabitDayRecord>, currentStreak: Int): Int {
        val totalDone = logs.values.count { isDayComplete(habit, it) }
        return maxOf(currentStreak, (totalDone / 2).coerceAtLeast(currentStreak))
    }

    private fun recomputeAllStats() {
        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // 14 days window (from today - 13 days to today)
        val dayWindow = (13 downTo 0).map { offset -> today.minusDays(offset.toLong()) }

        // Scheduled habits today for Discipline Overview:
        val scheduledHabitsToday = _habitsList.value.filter { h ->
            when (h.repeatType) {
                HabitRepeatType.EVERYDAY -> true
                HabitRepeatType.SPECIFIC_DAYS -> h.repeatDays.contains(today.dayOfWeek.value)
                HabitRepeatType.WEEKLY_X -> true
            }
        }

        var totalProgressPoints = 0f
        var completedTodayCount = 0
        val totalTodayCount = scheduledHabitsToday.size

        val statsList = _habitsList.value.map { habit ->
            val logsForHabit = _logsMap.value[habit.id] ?: emptyMap()
            val todayRecord = logsForHabit[today.format(dateFormatter)] ?: HabitDayRecord()
            val target = habit.targetValue.coerceAtLeast(1)
            val habitStartDate = getEffectiveStartDate(habit)

            // Today's progress fraction for this habit
            val todayFraction: Float = when (habit.type) {
                HabitType.TARGET -> {
                    if (todayRecord.state == HabitDayState.DONE) 1.0f
                    else (todayRecord.value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
                }
                HabitType.YES_NO -> {
                    if (todayRecord.state == HabitDayState.DONE) 1.0f else 0.0f
                }
            }

            val isTodayScheduled = when (habit.repeatType) {
                HabitRepeatType.EVERYDAY -> true
                HabitRepeatType.SPECIFIC_DAYS -> habit.repeatDays.contains(today.dayOfWeek.value)
                HabitRepeatType.WEEKLY_X -> true
            }

            if (isTodayScheduled) {
                totalProgressPoints += todayFraction
                if (todayFraction >= 1.0f || todayRecord.state == HabitDayState.DONE) {
                    completedTodayCount++
                }
            }

            // Build 14-day info
            val dayInfos = dayWindow.map { d ->
                val dStr = d.format(dateFormatter)
                val record = logsForHabit[dStr] ?: HabitDayRecord()
                val isRest = (habit.repeatType == HabitRepeatType.SPECIFIC_DAYS && !habit.repeatDays.contains(d.dayOfWeek.value))
                val isPast = d.isBefore(today)
                val isBeforeStart = d.isBefore(habitStartDate)
                // For days before start date, treat as UNMARKED (not started yet)
                val effectiveState = when {
                    isRest -> HabitDayState.UNMARKED
                    isBeforeStart -> HabitDayState.UNMARKED
                    isPast -> {
                        if (record.state == HabitDayState.DONE || (habit.type == HabitType.TARGET && record.value >= target && target > 0)) {
                            HabitDayState.DONE
                        } else {
                            HabitDayState.MISSED
                        }
                    }
                    else -> record.state
                }
                HabitDayInfo(
                    date = d,
                    dayNumber = d.dayOfMonth,
                    dayLabel = formatDayLabel(d.dayOfWeek),
                    isToday = d == today,
                    state = effectiveState,
                    value = record.value,
                    isRestDay = isRest,
                    note = record.note
                )
            }

            // Weekly count this week
            val startOfWeek = today.with(DayOfWeek.MONDAY)
            var weeklyDoneCount = 0
            var cur = startOfWeek
            while (!cur.isAfter(today.with(DayOfWeek.SUNDAY))) {
                val rec = logsForHabit[cur.format(dateFormatter)]
                if (isDayComplete(habit, rec)) {
                    weeklyDoneCount++
                }
                cur = cur.plusDays(1)
            }

            val currentStreak = calculateStreak(habit, logsForHabit, today)
            val bestStreak = calculateBestStreak(habit, logsForHabit, currentStreak)
            val adherence = calculateAdherence(habit, logsForHabit, today)

            val totalDone = logsForHabit.entries.count { (dateStr, record) ->
                val d = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()
                isDayComplete(habit, record) && (d == null || !d.isBefore(habitStartDate))
            }
            val totalMissed = logsForHabit.entries.count { (dateStr, record) ->
                val d = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()
                record.state == HabitDayState.MISSED && (d == null || !d.isBefore(habitStartDate))
            }

            HabitWithStats(
                habit = habit,
                streak = currentStreak,
                bestStreak = bestStreak,
                doneCount = totalDone,
                missedCount = totalMissed,
                adherenceRate = adherence,
                days = dayInfos,
                todayValue = todayRecord.value,
                todayProgressFraction = todayFraction,
                isTodayScheduled = isTodayScheduled,
                weeklyDoneThisWeek = weeklyDoneCount
            )
        }

        _habitsWithStats.value = statsList

        val pct = if (totalTodayCount > 0) ((totalProgressPoints / totalTodayCount) * 100).toInt().coerceIn(0, 100) else 0
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
}
