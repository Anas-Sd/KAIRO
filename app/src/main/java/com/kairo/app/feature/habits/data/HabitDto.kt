package com.kairo.app.feature.habits.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HabitDto(
    @SerialName("id") val id: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String = "YES_NO",
    @SerialName("target_value") val targetValue: Int = 1,
    @SerialName("target_unit") val targetUnit: String = "",
    @SerialName("repeat_type") val repeatType: String = "EVERYDAY",
    @SerialName("repeat_days") val repeatDays: String = "1,2,3,4,5,6,7",
    @SerialName("weekly_target_count") val weeklyTargetCount: Int = 3,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean = false,
    @SerialName("reminder_time") val reminderTime: String? = null,
    @SerialName("reminder_tone_uri") val reminderToneUri: String? = null,
    @SerialName("reminder_tone_title") val reminderToneTitle: String? = null,
    @SerialName("position") val position: Int = 0,
    @SerialName("note_prompt") val notePrompt: String = "BOTH",
    @SerialName("start_date") val startDate: String = "",
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class HabitLogDto(
    @SerialName("id") val id: String,
    @SerialName("habit_id") val habitId: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("date") val date: String, // "YYYY-MM-DD"
    @SerialName("state") val state: String = "DONE", // "DONE" or "MISSED"
    @SerialName("value") val value: Int? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
)
