package com.kairo.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.kairo.app.KairoApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSession(
    val code: String,
    val name: String
)

object AuthManager {
    private const val PREFS_NAME = "kairo_auth_prefs"
    private const val KEY_CODE = "user_access_code"
    private const val KEY_NAME = "user_display_name"

    private val prefs: SharedPreferences by lazy {
        KairoApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _sessionState = MutableStateFlow<UserSession?>(loadInitialSession())
    val sessionState: StateFlow<UserSession?> = _sessionState.asStateFlow()

    private fun loadInitialSession(): UserSession? {
        val code = prefs.getString(KEY_CODE, null)
        val name = prefs.getString(KEY_NAME, null)
        return if (!code.isNullOrBlank() && !name.isNullOrBlank()) {
            UserSession(code = code, name = name)
        } else {
            null
        }
    }

    val isLoggedIn: Boolean
        get() = _sessionState.value != null

    fun getUserCode(): String? = _sessionState.value?.code ?: prefs.getString(KEY_CODE, null)

    fun getUserName(): String? = _sessionState.value?.name ?: prefs.getString(KEY_NAME, null)

    fun saveSession(code: String, name: String) {
        prefs.edit()
            .putString(KEY_CODE, code.trim())
            .putString(KEY_NAME, name.trim())
            .apply()
        _sessionState.value = UserSession(code = code.trim(), name = name.trim())
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_CODE)
            .remove(KEY_NAME)
            .apply()
        _sessionState.value = null
    }
}
