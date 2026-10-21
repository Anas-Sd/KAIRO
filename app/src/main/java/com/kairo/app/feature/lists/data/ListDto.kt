package com.kairo.app.feature.lists.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskListDto(
    @SerialName("id") val id: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("name") val name: String,
    @SerialName("icon_name") val iconName: String = "list",
    @SerialName("position") val position: Int = 0,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ListItemDto(
    @SerialName("id") val id: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("list_id") val listId: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("icon_name") val iconName: String = "list",
    @SerialName("notes") val notes: String? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("target_date_millis") val targetDateMillis: Long? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("position") val position: Int = 0,
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)
