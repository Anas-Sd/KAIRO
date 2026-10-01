package com.kairo.app.data.remote

import com.kairo.app.data.local.TaskMetadataStore
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("priority") val priority: String = "MEDIUM",
    @SerialName("due_date") val dueDate: String = "Today",
    @SerialName("due_time") val dueTime: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("attachment_name") val attachmentName: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("section") val section: String = "TODAY",
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Task {
        val decoded = TaskMetadataCodec.decode(notes)
        val storedCompletedAt = TaskMetadataStore.getCompletedAt(id) ?: decoded.completedAt
        val effectiveCompletedAt = if (isCompleted) (storedCompletedAt ?: System.currentTimeMillis()) else null
        val storedUpdatedAt = TaskMetadataStore.getUpdatedAt(id) ?: decoded.updatedAt ?: createdAt
        val storedAttachment = TaskMetadataStore.getAttachmentUri(id) ?: decoded.attachmentUri

        return Task(
            id = id,
            title = title,
            notes = decoded.cleanNotes,
            priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.MEDIUM),
            dueDate = dueDate,
            dueDateMillis = null,
            dueTime = dueTime,
            location = location,
            attachmentName = attachmentName,
            attachmentUri = storedAttachment,
            isCompleted = isCompleted,
            section = runCatching { TaskSection.valueOf(section) }.getOrDefault(TaskSection.TODAY),
            createdAt = createdAt,
            updatedAt = storedUpdatedAt,
            completedAt = effectiveCompletedAt
        )
    }

    companion object {
        fun fromDomain(task: Task): TaskDto {
            val encodedNotes = TaskMetadataCodec.encode(
                userNotes = task.notes,
                completedAt = task.completedAt,
                updatedAt = task.updatedAt,
                attachmentUri = task.attachmentUri
            )
            return TaskDto(
                id = task.id,
                title = task.title,
                notes = encodedNotes,
                priority = task.priority.name,
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                location = task.location,
                attachmentName = task.attachmentName,
                isCompleted = task.isCompleted,
                section = task.section.name,
                createdAt = task.createdAt
            )
        }
    }
}
