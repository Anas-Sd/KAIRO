package com.kairo.app.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kairo.app.feature.auth.AuthFeatureFacade

/**
 * Backward-compatibility entry point for LoginScreen.
 * Delegates directly to [AuthFeatureFacade.LoginView].
 */
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    AuthFeatureFacade.LoginView(modifier = modifier)
}
