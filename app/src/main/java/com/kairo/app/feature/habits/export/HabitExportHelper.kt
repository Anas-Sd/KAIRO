package com.kairo.app.feature.habits.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.DisciplineOverviewStats
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayRecord
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitNoteHelper
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.model.HabitWithStats
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

enum class HabitExportFormat(
    val extension: String,
    val mimeType: String,
    val title: String,
    val description: String
) {
    HUMAN_READABLE(
        extension = "txt",
        mimeType = "text/plain",
        title = "Human Readable (.txt)",
        description = "Detailed report with executive discipline stats, habit cards, and day-by-day logs with notes"
    ),
    CSV(
        extension = "csv",
        mimeType = "text/csv",
        title = "CSV Spreadsheet (.csv)",
        description = "Standard tabular spreadsheet format with daily status, values, targets, and notes for Excel or Sheets"
    )
}

enum class HabitExportTimeline(val label: String, val description: String) {
    ALL_TIME("All Time", "Complete history from habit start date"),
    THIS_MONTH("This Month", "Current month logs and performance"),
    LAST_30_DAYS("Last 30 Days", "Past 30 days history"),
    THIS_WEEK("This Week", "Current week (Monday to Sunday)"),
    CUSTOM("Custom Range", "Select custom start and end dates")
}

object HabitExportHelper {

    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val fileTimestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun exportAndShare(
        context: Context,
        format: HabitExportFormat,
        timeline: HabitExportTimeline,
        habits: List<HabitWithStats>,
        overallStats: DisciplineOverviewStats? = null,
        customStartDate: LocalDate? = null,
        customEndDate: LocalDate? = null
    ): Boolean {
        if (habits.isEmpty()) {
            Toast.makeText(context, "No habits selected for export", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            val today = LocalDate.now()
            val (startDate, endDate) = resolveTimelineDates(timeline, habits, today, customStartDate, customEndDate)

            val allLogsMap = HabitRepository.logsMap.value

            val content = when (format) {
                HabitExportFormat.HUMAN_READABLE -> generateHumanReadable(
                    habits = habits,
                    overallStats = overallStats,
                    startDate = startDate,
                    endDate = endDate,
                    timeline = timeline,
                    logsMap = allLogsMap
                )
                HabitExportFormat.CSV -> generateCsv(
                    habits = habits,
                    startDate = startDate,
                    endDate = endDate,
                    logsMap = allLogsMap
                )
            }

            val timestamp = fileTimestampFormatter.format(Date())
            val safeScope = if (habits.size == 1) {
                habits.first().habit.title.replace("[^a-zA-Z0-9]".toRegex(), "_").take(15)
            } else {
                "${habits.size}Habits"
            }
            val timelinePrefix = timeline.name.lowercase().replace("_", "")
            val fileName = "KAIRO_Habits_${safeScope}_${timelinePrefix}_$timestamp.${format.extension}"

            // 1. Save file to Downloads/KAIRO/
            saveToDownloads(context, fileName, format.mimeType, content)

            // 2. Open Android Native Share Sheet
            shareFile(context, fileName, format.mimeType, content)

            Toast.makeText(
                context,
                "Saved to Downloads/KAIRO & opening share...",
                Toast.LENGTH_LONG
            ).show()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
    }

    private fun resolveTimelineDates(
        timeline: HabitExportTimeline,
        habits: List<HabitWithStats>,
        today: LocalDate,
        customStartDate: LocalDate?,
        customEndDate: LocalDate?
    ): Pair<LocalDate, LocalDate> {
        return when (timeline) {
            HabitExportTimeline.ALL_TIME -> {
                val earliestHabitStart = habits.minOfOrNull {
                    HabitRepository.getEffectiveStartDate(it.habit)
                } ?: today.minusDays(30)
                earliestHabitStart to today
            }
            HabitExportTimeline.THIS_MONTH -> {
                today.withDayOfMonth(1) to today
            }
            HabitExportTimeline.LAST_30_DAYS -> {
                today.minusDays(29) to today
            }
            HabitExportTimeline.THIS_WEEK -> {
                today.with(DayOfWeek.MONDAY) to today
            }
            HabitExportTimeline.CUSTOM -> {
                val s = customStartDate ?: today.minusDays(7)
                val e = customEndDate ?: today
                if (s.isAfter(e)) e to s else s to e
            }
        }
    }

    fun generateHumanReadable(
        habits: List<HabitWithStats>,
        overallStats: DisciplineOverviewStats?,
        startDate: LocalDate,
        endDate: LocalDate,
        timeline: HabitExportTimeline,
        logsMap: Map<String, Map<String, HabitDayRecord>>
    ): String {
        val nowStr = dateTimeFormatter.format(Date())
        val sb = StringBuilder()

        sb.appendLine("=".repeat(80))
        sb.appendLine("                           KAIRO HABITS REPORT")
        sb.appendLine("=".repeat(80))
        sb.appendLine("Export Date: $nowStr")
        sb.appendLine("Timeline: ${timeline.label.uppercase()} ($startDate to $endDate)")
        sb.appendLine("Total Habits Exported: ${habits.size}")
        sb.appendLine("=".repeat(80))
        sb.appendLine()

        // 1. EXECUTIVE OVERVIEW (OVERALL DISCIPLINE)
        sb.appendLine("-".repeat(80))
        sb.appendLine("EXECUTIVE OVERVIEW (OVERALL DISCIPLINE)")
        sb.appendLine("-".repeat(80))
        if (overallStats != null) {
            sb.appendLine("• Overall Discipline Score: ${overallStats.percentage}%")
            sb.appendLine("• Habits Completed Today: ${overallStats.completedToday} / ${overallStats.totalToday}")
            sb.appendLine("• Habits Remaining Today: ${overallStats.remainingToday}")
            sb.appendLine("• Active Discipline Streak: ${overallStats.streakDays} Days")
        } else {
            val avgAdherence = if (habits.isNotEmpty()) habits.map { it.adherenceRate }.average().toInt() else 0
            sb.appendLine("• Average Habit Adherence: $avgAdherence%")
        }
        sb.appendLine("-".repeat(80))
        sb.appendLine()

        // 2. DETAILED BREAKDOWN PER HABIT
        sb.appendLine("=".repeat(80))
        sb.appendLine("HABIT DEEP DIVE & DAILY TIMELINE LOGS")
        sb.appendLine("=".repeat(80))
        sb.appendLine()

        habits.forEachIndexed { index, habitWithStats ->
            val habit = habitWithStats.habit
            val habitLogs = logsMap[habit.id] ?: emptyMap()
            val habitStartDate = HabitRepository.getEffectiveStartDate(habit)

            val typeDesc = when (habit.type) {
                HabitType.YES_NO -> "Yes / No Check-in"
                HabitType.TARGET -> "Target (${habit.targetValue} ${habit.targetUnit} / day)"
            }

            val repeatDesc = when (habit.repeatType) {
                HabitRepeatType.EVERYDAY -> "Everyday"
                HabitRepeatType.SPECIFIC_DAYS -> {
                    val daysStr = habit.repeatDays.sorted().joinToString(", ") { d ->
                        DayOfWeek.of(d).name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    }
                    "Specific Days ($daysStr)"
                }
                HabitRepeatType.WEEKLY_X -> "Weekly ${habit.weeklyTargetCount} times"
            }

            val streakUnit = when (habit.repeatType) {
                HabitRepeatType.WEEKLY_X -> "Weeks"
                HabitRepeatType.SPECIFIC_DAYS -> "Sessions"
                HabitRepeatType.EVERYDAY -> "Days"
            }

            // Calculate metrics specifically for the selected timeline window
            var windowScheduled = 0
            var windowDone = 0
            var windowMissed = 0
            var windowRest = 0

            var curDate = startDate
            while (!curDate.isAfter(endDate)) {
                val isBeforeStart = curDate.isBefore(habitStartDate)
                val isRestDay = !isBeforeStart && when (habit.repeatType) {
                    HabitRepeatType.SPECIFIC_DAYS -> !habit.repeatDays.contains(curDate.dayOfWeek.value)
                    else -> false
                }

                if (!isBeforeStart) {
                    if (isRestDay) {
                        windowRest++
                    } else {
                        windowScheduled++
                        val rec = habitLogs[curDate.format(isoDateFormatter)]
                        if (HabitRepository.isDayComplete(habit, rec)) {
                            windowDone++
                        } else if (rec?.state == HabitDayState.MISSED) {
                            windowMissed++
                        }
                    }
                }
                curDate = curDate.plusDays(1)
            }

            val windowAdherence = if (windowScheduled > 0) {
                String.format(Locale.US, "%.1f", (windowDone.toFloat() / windowScheduled) * 100f)
            } else {
                "100.0"
            }

            sb.appendLine("${index + 1}. [${habit.type.name}] ${habit.title}")
            sb.appendLine("   • Target / Type: $typeDesc")
            sb.appendLine("   • Repeat Schedule: $repeatDesc")
            sb.appendLine("   • Habit Start Date: $habitStartDate")
            sb.appendLine("   • Current Streak: ${habitWithStats.streak} $streakUnit | Best Streak: ${habitWithStats.bestStreak} $streakUnit")
            sb.appendLine("   • All-Time Discipline Adherence: ${String.format(Locale.US, "%.1f", habitWithStats.adherenceRate)}%")
            sb.appendLine("   • Timeline Performance ($startDate to $endDate):")
            sb.appendLine("     - Scheduled Days: $windowScheduled")
            sb.appendLine("     - Completed Days: $windowDone")
            sb.appendLine("     - Missed Days: $windowMissed")
            sb.appendLine("     - Rest Days: $windowRest")
            sb.appendLine("     - Timeline Adherence: $windowAdherence%")
            sb.appendLine("   • Notes Prompt Setting: ${habit.notePrompt.name}")
            sb.appendLine()
            sb.appendLine("   DAILY TIMELINE LOGS (Recent First):")
            sb.appendLine("   " + "-".repeat(74))

            // Loop days backwards (recent first)
            var logDate = endDate
            var hasLogs = false
            while (!logDate.isBefore(startDate)) {
                val isBeforeStart = logDate.isBefore(habitStartDate)
                val isRestDay = !isBeforeStart && when (habit.repeatType) {
                    HabitRepeatType.SPECIFIC_DAYS -> !habit.repeatDays.contains(logDate.dayOfWeek.value)
                    else -> false
                }

                val rec = habitLogs[logDate.format(isoDateFormatter)]
                val dayOfWeekName = logDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

                val statusStr = when {
                    isBeforeStart -> "NOT STARTED YET"
                    isRestDay -> "REST DAY"
                    rec == null || rec.state == HabitDayState.UNMARKED -> {
                        if (logDate == LocalDate.now()) "IN PROGRESS (Today)" else "UNMARKED"
                    }
                    HabitRepository.isDayComplete(habit, rec) -> {
                        if (habit.type == HabitType.TARGET) "DONE (${rec.value}/${habit.targetValue} ${habit.targetUnit}, 100%)"
                        else "DONE"
                    }
                    rec.state == HabitDayState.MISSED -> {
                        if (habit.type == HabitType.TARGET) "MISSED (${rec.value}/${habit.targetValue} ${habit.targetUnit})"
                        else "MISSED"
                    }
                    rec.value > 0 -> "PARTIAL (${rec.value}/${habit.targetValue} ${habit.targetUnit})"
                    else -> "UNMARKED"
                }

                val formattedNote = HabitNoteHelper.formatDisplayNote(rec?.note)

                sb.appendLine("   • $logDate ($dayOfWeekName): $statusStr")
                if (formattedNote.isNotBlank()) {
                    sb.appendLine("     📝 Notes: \"$formattedNote\"")
                }

                hasLogs = true
                logDate = logDate.minusDays(1)
            }

            if (!hasLogs) {
                sb.appendLine("   (No logs recorded in this timeline window)")
            }

            sb.appendLine("   " + "-".repeat(74))
            sb.appendLine()
        }

        return sb.toString()
    }

    fun generateCsv(
        habits: List<HabitWithStats>,
        startDate: LocalDate,
        endDate: LocalDate,
        logsMap: Map<String, Map<String, HabitDayRecord>>
    ): String {
        val sb = StringBuilder()
        // UTF-8 BOM for Microsoft Excel / Google Sheets compatibility
        sb.append("\uFEFF")

        // Header Row
        val headers = listOf(
            "Date",
            "Day",
            "Habit Title",
            "Habit Type",
            "Target",
            "Logged Value",
            "Daily Progress %",
            "Status",
            "Notes",
            "Current Streak",
            "Best Streak",
            "Overall Discipline %",
            "Schedule"
        )
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })

        habits.forEach { habitWithStats ->
            val habit = habitWithStats.habit
            val habitLogs = logsMap[habit.id] ?: emptyMap()
            val habitStartDate = HabitRepository.getEffectiveStartDate(habit)

            val targetDesc = when (habit.type) {
                HabitType.YES_NO -> "Done / Missed"
                HabitType.TARGET -> "${habit.targetValue} ${habit.targetUnit}"
            }

            val repeatDesc = when (habit.repeatType) {
                HabitRepeatType.EVERYDAY -> "Everyday"
                HabitRepeatType.SPECIFIC_DAYS -> "Specific Days"
                HabitRepeatType.WEEKLY_X -> "Weekly ${habit.weeklyTargetCount}x"
            }

            val disciplinePercentStr = String.format(Locale.US, "%.1f%%", habitWithStats.adherenceRate)

            var logDate = endDate
            while (!logDate.isBefore(startDate)) {
                val isBeforeStart = logDate.isBefore(habitStartDate)
                val isRestDay = !isBeforeStart && when (habit.repeatType) {
                    HabitRepeatType.SPECIFIC_DAYS -> !habit.repeatDays.contains(logDate.dayOfWeek.value)
                    else -> false
                }

                val rec = habitLogs[logDate.format(isoDateFormatter)]
                val dayOfWeekName = logDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

                val (statusStr, progressPct, loggedValStr) = when {
                    isBeforeStart -> Triple("NOT_STARTED", "0%", "0")
                    isRestDay -> Triple("REST_DAY", "-", "-")
                    rec == null || rec.state == HabitDayState.UNMARKED -> {
                        if (logDate == LocalDate.now()) Triple("IN_PROGRESS", "0%", "0") else Triple("UNMARKED", "0%", "0")
                    }
                    HabitRepository.isDayComplete(habit, rec) -> {
                        val pct = if (habit.type == HabitType.TARGET && habit.targetValue > 0) {
                            val frac = (rec.value.toFloat() / habit.targetValue) * 100f
                            "${frac.toInt()}%"
                        } else "100%"
                        val valStr = if (habit.type == HabitType.TARGET) "${rec.value} ${habit.targetUnit}" else "Done"
                        Triple("DONE", pct, valStr)
                    }
                    rec.state == HabitDayState.MISSED -> {
                        val valStr = if (habit.type == HabitType.TARGET) "${rec.value} ${habit.targetUnit}" else "Missed"
                        Triple("MISSED", "0%", valStr)
                    }
                    rec.value > 0 -> {
                        val frac = (rec.value.toFloat() / habit.targetValue.coerceAtLeast(1)) * 100f
                        Triple("PARTIAL", "${frac.toInt()}%", "${rec.value} ${habit.targetUnit}")
                    }
                    else -> Triple("UNMARKED", "0%", "0")
                }

                val notes = HabitNoteHelper.formatDisplayNote(rec?.note)

                val row = listOf(
                    logDate.toString(),
                    dayOfWeekName,
                    habit.title,
                    habit.type.name,
                    targetDesc,
                    loggedValStr,
                    progressPct,
                    statusStr,
                    notes,
                    habitWithStats.streak.toString(),
                    habitWithStats.bestStreak.toString(),
                    disciplinePercentStr,
                    repeatDesc
                )

                sb.appendLine(row.joinToString(",") { escapeCsv(it) })
                logDate = logDate.minusDays(1)
            }
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun saveToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String
    ): Uri? {
        val bytes = content.toByteArray(Charsets.UTF_8)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/KAIRO")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                    os.flush()
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val kairoDir = File(downloadsDir, "KAIRO")
            if (!kairoDir.exists()) {
                kairoDir.mkdirs()
            }
            val targetFile = File(kairoDir, fileName)
            targetFile.writeBytes(bytes)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            Uri.fromFile(targetFile)
        }
    }

    private fun shareFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String
    ) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val shareFile = File(exportsDir, fileName)
        shareFile.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, "KAIRO Habits Export: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share Habits via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
