package com.kairo.app.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/**
 * AuthFeatureFacade
 *
 * The single public service window ("door") for the Authentication & Session Box.
 * External features, UI screens, repositories, and services interact with authentication
 * and user identity strictly through this facade.
 */
object AuthFeatureFacade {

    val sessionState: StateFlow<UserSession?>
        get() = AuthManager.sessionState

    val isLoggedIn: Boolean
        get() = AuthManager.isLoggedIn

    fun getUserCode(): String? = AuthManager.getUserCode()

    fun getUserName(): String? = AuthManager.getUserName()

    fun saveSession(code: String, name: String) {
        AuthManager.saveSession(code, name)
    }

    fun clearSession() {
        AuthManager.clearSession()
    }

    /**
     * Public entry composable for the authentication / login interface.
     */
    @Composable
    fun LoginView(modifier: Modifier = Modifier) {
        LoginScreen(modifier = modifier)
    }
}
