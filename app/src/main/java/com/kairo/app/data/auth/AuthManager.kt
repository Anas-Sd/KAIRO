package com.kairo.app.data.auth

import com.kairo.app.feature.auth.AuthFeatureFacade
import kotlinx.coroutines.flow.StateFlow

typealias UserSession = com.kairo.app.feature.auth.UserSession

/**
 * Backward-compatibility delegator for legacy callers.
 * New callers should use [AuthFeatureFacade].
 */
object AuthManager {
    val sessionState: StateFlow<UserSession?>
        get() = AuthFeatureFacade.sessionState

    val isLoggedIn: Boolean
        get() = AuthFeatureFacade.isLoggedIn

    fun getUserCode(): String? = AuthFeatureFacade.getUserCode()

    fun getUserName(): String? = AuthFeatureFacade.getUserName()

    fun saveSession(code: String, name: String) = AuthFeatureFacade.saveSession(code, name)

    fun clearSession() = AuthFeatureFacade.clearSession()
}
