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
}
