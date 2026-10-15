package com.kairo.app.feature.tasks.export

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
import com.kairo.app.data.model.Task
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TaskExportFormat(
    val extension: String,
    val mimeType: String,
    val title: String,
    val description: String
) {
    HUMAN_READABLE(
        extension = "txt",
        mimeType = "text/plain",
        title = "Human Readable (.txt)",
        description = "Clean checklist summary with task details, priorities, notes, and subtasks"
    ),
    CSV(
        extension = "csv",
        mimeType = "text/csv",
        title = "CSV Spreadsheet (.csv)",
        description = "Standard spreadsheet tabular data compatible with Excel and Google Sheets"
    )
}

object TaskExportHelper {

    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fileTimestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun exportAndShare(
        context: Context,
        format: TaskExportFormat,
        tasks: List<Task>,
        allTasks: List<Task> = tasks,
        isFiltered: Boolean = false
    ): Boolean {
        if (tasks.isEmpty()) {
            Toast.makeText(context, "No tasks to export", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            val content = when (format) {
                TaskExportFormat.HUMAN_READABLE -> generateHumanReadable(tasks, allTasks, isFiltered)
                TaskExportFormat.CSV -> generateCsv(tasks, allTasks)
            }

            val timestamp = fileTimestampFormatter.format(Date())
            val scopePrefix = if (isFiltered) "Filtered" else "All"
            val fileName = "KAIRO_Tasks_${scopePrefix}_$timestamp.${format.extension}"

            // 1. Save to Downloads/KAIRO/
            saveToDownloads(context, fileName, format.mimeType, content)

            // 2. Open Share Sheet via FileProvider
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

    fun generateHumanReadable(
        tasks: List<Task>,
        allTasks: List<Task>,
        isFiltered: Boolean
    ): String {
        val now = dateTimeFormatter.format(Date())
        val pending = tasks.filter { !it.isCompleted }
        val completed = tasks.filter { it.isCompleted }

        // Map parent ID to children for subtask rendering
        val subtasksByParent = allTasks.groupBy { it.parentId }

        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("KAIRO TASKS EXPORT")
        sb.appendLine("==================================================")
        sb.appendLine("Exported: $now")
        sb.appendLine("Scope: ${if (isFiltered) "Filtered Tasks (${tasks.size} tasks)" else "All Tasks (${tasks.size} tasks)"}")
        sb.appendLine("Summary: ${tasks.size} Total | ${pending.size} Pending | ${completed.size} Completed")
        sb.appendLine("==================================================")
        sb.appendLine()

        // --- PENDING TASKS ---
        sb.appendLine("[PENDING TASKS] (${pending.size})")
        sb.appendLine("--------------------------------------------------")
        if (pending.isEmpty()) {
            sb.appendLine("(No pending tasks)")
            sb.appendLine()
        } else {
            pending.forEachIndexed { index, task ->
                sb.appendLine("${index + 1}. [ ] ${task.title}")
                sb.appendLine("   • Priority: ${task.priority.label}")
                val dueText = buildString {
                    append(getSanitizedDueDate(task))
                    if (!task.dueTime.isNullOrBlank()) {
                        append(" at ${task.dueTime}")
                    }
                }
                sb.appendLine("   • Due: $dueText")
                sb.appendLine("   • Section: ${task.section.title}")
                if (!task.location.isNullOrBlank()) {
                    sb.appendLine("   • Location: ${task.location}")
                }
                if (!task.notes.isNullOrBlank()) {
                    sb.appendLine("   • Notes: ${task.notes.replace("\n", "\n     ")}")
                }

                // Subtasks under this task
                val children = subtasksByParent[task.id] ?: emptyList()
                if (children.isNotEmpty()) {
                    sb.appendLine("   • Subtasks (${children.size}):")
                    children.forEach { child ->
                        val check = if (child.isCompleted) "[✓]" else "[ ]"
                        sb.appendLine("     - $check ${child.title}")
                    }
                }
                sb.appendLine()
            }
        }

        // --- COMPLETED TASKS ---
        sb.appendLine("[COMPLETED TASKS] (${completed.size})")
        sb.appendLine("--------------------------------------------------")
        if (completed.isEmpty()) {
            sb.appendLine("(No completed tasks)")
            sb.appendLine()
        } else {
            completed.forEachIndexed { index, task ->
                sb.appendLine("${index + 1}. [✓] ${task.title}")
                sb.appendLine("   • Priority: ${task.priority.label}")
                if (task.completedAt != null) {
                    sb.appendLine("   • Completed: ${dateTimeFormatter.format(Date(task.completedAt))}")
                }
                sb.appendLine("   • Due Was: ${getSanitizedDueDate(task)}${task.dueTime?.let { " at $it" } ?: ""}")
                if (!task.location.isNullOrBlank()) {
                    sb.appendLine("   • Location: ${task.location}")
                }
                if (!task.notes.isNullOrBlank()) {
                    sb.appendLine("   • Notes: ${task.notes.replace("\n", "\n     ")}")
                }

                val children = subtasksByParent[task.id] ?: emptyList()
                if (children.isNotEmpty()) {
                    sb.appendLine("   • Subtasks (${children.size}):")
                    children.forEach { child ->
                        val check = if (child.isCompleted) "[✓]" else "[ ]"
                        sb.appendLine("     - $check ${child.title}")
                    }
                }
                sb.appendLine()
            }
        }

        sb.appendLine("==================================================")
        sb.appendLine("Exported from KAIRO")
        sb.appendLine("==================================================")

        return sb.toString()
    }

    fun generateCsv(
        tasks: List<Task>,
        allTasks: List<Task>
    ): String {
        val subtasksByParent = allTasks.groupBy { it.parentId }

        val sb = StringBuilder()
        // CSV Header
        val headers = listOf(
            "Task ID",
            "Title",
            "Status",
            "Section",
            "Priority",
            "Due Date",
            "Due Time",
            "Location",
            "Notes",
            "Parent Task ID",
            "Subtasks Count",
            "Created At",
            "Completed At"
        )
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })

        for (task in tasks) {
            val status = if (task.isCompleted) "Completed" else "Pending"
            val subtasksCount = (subtasksByParent[task.id]?.size ?: 0).toString()
            val createdAtStr = dateTimeFormatter.format(Date(task.createdAt))
            val completedAtStr = task.completedAt?.let { dateTimeFormatter.format(Date(it)) } ?: ""

            val displayDueDate = getSanitizedDueDate(task)
            val row = listOf(
                task.id,
                task.title,
                status,
                task.section.title,
                task.priority.label,
                displayDueDate,
                task.dueTime ?: "",
                task.location ?: "",
                task.notes ?: "",
                task.parentId ?: "",
                subtasksCount,
                createdAtStr,
                completedAtStr
            )
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun getSanitizedDueDate(task: Task): String {
        return if (task.dueDate.equals("SNOOZED", ignoreCase = true)) {
            com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, task.dueDateMillis ?: task.createdAt)
        } else {
            task.dueDate
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
            putExtra(Intent.EXTRA_TEXT, "KAIRO Tasks Export: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share Tasks via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
