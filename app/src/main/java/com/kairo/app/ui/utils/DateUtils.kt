package com.kairo.app.ui.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    fun getTodayDisplayDate(): String {
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())
    }

    /**
     * Parses any natural or formatted date string into accurate epoch milliseconds.
     */
    fun parseDueDateMillis(dueDateStr: String?, fallback: Long = System.currentTimeMillis()): Long {
        val raw = dueDateStr?.trim().orEmpty()
        if (raw.isBlank()) return fallback

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when {
            raw.contains("Today", ignoreCase = true) -> cal.timeInMillis
            raw.contains("Tomorrow", ignoreCase = true) -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            raw.contains("Yesterday", ignoreCase = true) -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.timeInMillis
            }
            raw.contains("day after tomorrow", ignoreCase = true) -> {
                cal.add(Calendar.DAY_OF_YEAR, 2)
                cal.timeInMillis
            }
            else -> {
                // Check if user specified a day of the week (e.g. "Monday", "Friday")
                val daysOfWeek = mapOf(
                    "sunday" to Calendar.SUNDAY,
                    "monday" to Calendar.MONDAY,
                    "tuesday" to Calendar.TUESDAY,
                    "wednesday" to Calendar.WEDNESDAY,
                    "thursday" to Calendar.THURSDAY,
                    "friday" to Calendar.FRIDAY,
                    "saturday" to Calendar.SATURDAY
                )
                val targetDay = daysOfWeek.entries.find { raw.contains(it.key, ignoreCase = true) }?.value
                if (targetDay != null) {
                    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = targetDay - currentDay
                    if (daysToAdd <= 0) daysToAdd += 7
                    cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                    return cal.timeInMillis
                }

                val formats = arrayOf(
                    "MMM d", "MMM dd", "yyyy-MM-dd", "dd/MM/yyyy",
                    "MMM dd, yyyy", "dd MMM yyyy", "EEE, MMM d", "d MMM"
                )
                for (fmt in formats) {
                    try {
                        val parsed = SimpleDateFormat(fmt, Locale.getDefault()).parse(raw)
                        if (parsed != null) {
                            val pCal = Calendar.getInstance().apply { time = parsed }
                            if (pCal.get(Calendar.YEAR) <= 1970) {
                                pCal.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                            }
                            pCal.set(Calendar.HOUR_OF_DAY, 12)
                            pCal.set(Calendar.MINUTE, 0)
                            pCal.set(Calendar.SECOND, 0)
                            pCal.set(Calendar.MILLISECOND, 0)
                            return pCal.timeInMillis
                        }
                    } catch (_: Exception) {}
                }
                fallback
            }
        }
    }

    /**
     * Resolves and formats any date input into a clean date representation (e.g. "Oct 3").
     * Replaces relative day references like "Today", "Tomorrow", "Yesterday" with actual dates.
     */
    fun formatDisplayDate(dueDate: String?, dueDateMillis: Long? = null): String {
        if (dueDateMillis != null && dueDateMillis > 0) {
            return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(dueDateMillis))
        }

        val raw = dueDate?.trim() ?: ""
        if (raw.isEmpty()) {
            return getTodayDisplayDate()
        }

        val millis = parseDueDateMillis(raw)
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
    }

    /**
     * Calculates the next recurring due date and epoch millis for a task.
     * Supports "DAYS" (with repeatDays e.g. "MON,TUE,WED,THU,FRI,SAT,SUN" or "MON,WED"),
     * "EVERYDAY", "DAILY", "MONTHLY_DATES", and "BOTH_MONTHLY".
     */
    fun calculateNextRepeatDueDate(
        currentDueDateMillis: Long?,
        currentDueDateStr: String?,
        repeatType: String?,
        repeatDays: String?,
        repeatDates: String?
    ): Pair<String, Long>? {
        if (repeatType.isNullOrBlank() || repeatType.equals("NONE", ignoreCase = true)) {
            return null
        }

        val baseMillis = currentDueDateMillis ?: parseDueDateMillis(currentDueDateStr)
        val cal = Calendar.getInstance().apply { timeInMillis = baseMillis }

        // Search relative to today if the base due date is in the past
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.before(todayStart)) {
            cal.timeInMillis = todayStart.timeInMillis
        }

        when (repeatType.uppercase(Locale.US)) {
            "EVERYDAY", "DAILY" -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val nextMillis = cal.timeInMillis
                return Pair(formatDisplayDate(null, nextMillis), nextMillis)
            }
            "DAYS" -> {
                val dayMap = mapOf(
                    "SUN" to Calendar.SUNDAY,
                    "MON" to Calendar.MONDAY,
                    "TUE" to Calendar.TUESDAY,
                    "WED" to Calendar.WEDNESDAY,
                    "THU" to Calendar.THURSDAY,
                    "FRI" to Calendar.FRIDAY,
                    "SAT" to Calendar.SATURDAY
                )
                val targetDays = repeatDays?.split(",")?.map { it.trim().uppercase(Locale.US) }?.mapNotNull { dayMap[it] }?.toSet()
                if (targetDays.isNullOrEmpty() || targetDays.size == 7) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val nextMillis = cal.timeInMillis
                    return Pair(formatDisplayDate(null, nextMillis), nextMillis)
                }

                // Advance day-by-day up to 14 days to find next scheduled day
                for (i in 1..14) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    if (targetDays.contains(cal.get(Calendar.DAY_OF_WEEK))) {
                        val nextMillis = cal.timeInMillis
                        return Pair(formatDisplayDate(null, nextMillis), nextMillis)
                    }
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val nextMillis = cal.timeInMillis
                return Pair(formatDisplayDate(null, nextMillis), nextMillis)
            }
            "MONTHLY_DATES", "BOTH_MONTHLY" -> {
                cal.add(Calendar.MONTH, 1)
                val nextMillis = cal.timeInMillis
                return Pair(formatDisplayDate(null, nextMillis), nextMillis)
            }
            else -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val nextMillis = cal.timeInMillis
                return Pair(formatDisplayDate(null, nextMillis), nextMillis)
            }
        }
    }

    /**
     * Formats the remaining time until targetMillis into:
     * - "X min" if < 60 minutes
     * - "X hrs : Y min" (or "1 hr : Y min") if < 24 hours
     * - "X days : Y hrs : Z min" (or "1 day : ...") if >= 24 hours
     */
    fun formatRemainingTime(targetMillis: Long, now: Long = System.currentTimeMillis()): String? {
        val diffMs = targetMillis - now
        if (diffMs <= 0) return null

        val totalMinutes = (diffMs + 59999L) / 60000L
        if (totalMinutes < 60L) {
            val mins = totalMinutes.coerceAtLeast(1L)
            return "$mins min"
        }

        val totalHours = totalMinutes / 60L
        val remainingMins = totalMinutes % 60L

        if (totalHours < 24L) {
            val hrUnit = if (totalHours == 1L) "hr" else "hrs"
            return "$totalHours $hrUnit : $remainingMins min"
        }

        val days = totalHours / 24L
        val remainingHours = totalHours % 24L
        val dayUnit = if (days == 1L) "day" else "days"
        val hrUnit = if (remainingHours == 1L) "hr" else "hrs"

        return "$days $dayUnit : $remainingHours $hrUnit : $remainingMins min"
    }

    /**
     * Computes the next active alarm trigger millis for a task.
     * Evaluates active snooze time first, then scheduled trigger time.
     * Returns null if completed or already in the past.
     */
    fun getTaskAlarmTargetMillis(task: com.kairo.app.data.model.Task, now: Long = System.currentTimeMillis()): Long? {
        if (task.isCompleted) return null
        val snooze = task.snoozeUntilMillis
        if (snooze != null && snooze > now) {
            return snooze
        }
        val trigger = com.kairo.app.alarm.AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
        if (trigger != null && trigger > now) {
            return trigger
        }
        return null
    }

    /**
     * Finds the active task with the closest upcoming alarm across a list of tasks.
     */
    fun getNextUpcomingAlarmTask(tasks: List<com.kairo.app.data.model.Task>, now: Long = System.currentTimeMillis()): Pair<com.kairo.app.data.model.Task, Long>? {
        return tasks
            .filter { !it.isCompleted }
            .mapNotNull { task ->
                val target = getTaskAlarmTargetMillis(task, now)
                if (target != null) task to target else null
            }
            .minByOrNull { it.second }
    }
}

