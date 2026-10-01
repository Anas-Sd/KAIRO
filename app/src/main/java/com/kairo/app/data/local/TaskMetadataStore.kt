package com.kairo.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.kairo.app.KairoApplication

object TaskMetadataStore {

    private const val PREF_NAME = "kairo_task_metadata"
    private const val PREFIX_COMPLETED_AT = "completed_at_"
    private const val PREFIX_UPDATED_AT = "updated_at_"
    private const val PREFIX_ATTACHMENT_URI = "attachment_uri_"

    private val prefs: SharedPreferences by lazy {
        KairoApplication.instance.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveCompletedAt(taskId: String, timestamp: Long?) {
        val editor = prefs.edit()
        if (timestamp != null) {
            editor.putLong(PREFIX_COMPLETED_AT + taskId, timestamp)
        } else {
            editor.remove(PREFIX_COMPLETED_AT + taskId)
        }
        editor.apply()
    }

    fun getCompletedAt(taskId: String): Long? {
        val key = PREFIX_COMPLETED_AT + taskId
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun saveUpdatedAt(taskId: String, timestamp: Long) {
        prefs.edit().putLong(PREFIX_UPDATED_AT + taskId, timestamp).apply()
    }

    fun getUpdatedAt(taskId: String): Long? {
        val key = PREFIX_UPDATED_AT + taskId
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun saveAttachmentUri(taskId: String, uri: String?) {
        val editor = prefs.edit()
        if (uri != null) {
            editor.putString(PREFIX_ATTACHMENT_URI + taskId, uri)
        } else {
            editor.remove(PREFIX_ATTACHMENT_URI + taskId)
        }
        editor.apply()
    }

    fun getAttachmentUri(taskId: String): String? {
        val key = PREFIX_ATTACHMENT_URI + taskId
        return if (prefs.contains(key)) prefs.getString(key, null) else null
    }

    fun clear(taskId: String) {
        prefs.edit()
            .remove(PREFIX_COMPLETED_AT + taskId)
            .remove(PREFIX_UPDATED_AT + taskId)
            .remove(PREFIX_ATTACHMENT_URI + taskId)
            .apply()
    }
}
