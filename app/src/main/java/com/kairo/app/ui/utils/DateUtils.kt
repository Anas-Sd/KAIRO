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

        return when {
            raw.contains("Today", ignoreCase = true) -> {
                getTodayDisplayDate()
            }
            raw.contains("Tomorrow", ignoreCase = true) -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
            }
            raw.contains("Yesterday", ignoreCase = true) -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
            }
            else -> {
                try {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsed = isoFormat.parse(raw)
                    if (parsed != null) {
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(parsed)
                    } else {
                        raw
                    }
                } catch (_: Exception) {
                    raw
                }
            }
        }
    }
}
