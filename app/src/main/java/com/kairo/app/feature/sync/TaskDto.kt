package com.kairo.app.feature.sync

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
    @SerialName("due_date_millis") val dueDateMillis: Long? = null,
    @SerialName("due_time") val dueTime: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("attachment_name") val attachmentName: String? = null,
    @SerialName("attachment_uri") val attachmentUri: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("section") val section: String = "TODAY",
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("alarm_tone_uri") val alarmToneUri: String? = null,
    @SerialName("alarm_tone_title") val alarmToneTitle: String? = null,
    @SerialName("repeat_type") val repeatType: String? = null,
    @SerialName("repeat_days") val repeatDays: String? = null,
    @SerialName("repeat_dates") val repeatDates: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("position") val position: Int = 0,
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("radius_meters") val locationRadius: Int? = 500
) {
    fun toDomain(): Task {
        return Task(
            id = id,
            title = title,
            notes = notes,
            priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.LOW),
            dueDate = dueDate,
            dueDateMillis = dueDateMillis,
            dueTime = dueTime,
            location = location,
            attachmentName = attachmentName,
            attachmentUri = attachmentUri,
            isCompleted = isCompleted,
            section = runCatching { TaskSection.valueOf(section) }.getOrDefault(TaskSection.TODAY),
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = if (isCompleted) completedAt else null,
            alarmToneUri = alarmToneUri,
            alarmToneTitle = alarmToneTitle,
            repeatType = repeatType,
            repeatDays = repeatDays,
            repeatDates = repeatDates,
            parentId = parentId,
            position = position,
            latitude = latitude,
            longitude = longitude,
            locationRadius = locationRadius ?: 500
        )
    }

    companion object {
        fun fromDomain(task: Task, userCode: String? = null): TaskDto {
            return TaskDto(
                id = task.id,
                title = task.title,
                notes = task.notes,
                priority = task.priority.name,
                dueDate = task.dueDate,
                dueDateMillis = task.dueDateMillis,
                dueTime = task.dueTime,
                location = task.location,
                attachmentName = task.attachmentName,
                attachmentUri = task.attachmentUri,
                isCompleted = task.isCompleted,
                section = task.section.name,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                completedAt = task.completedAt,
                alarmToneUri = task.alarmToneUri,
                alarmToneTitle = task.alarmToneTitle,
                repeatType = task.repeatType,
                repeatDays = task.repeatDays,
                repeatDates = task.repeatDates,
                parentId = task.parentId,
                position = task.position,
                userCode = userCode ?: com.kairo.app.data.auth.AuthManager.getUserCode(),
                latitude = task.latitude,
                longitude = task.longitude,
                locationRadius = task.locationRadius
            )
        }
    }
}
