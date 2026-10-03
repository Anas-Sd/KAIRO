package com.kairo.app.feature.settings

import androidx.compose.runtime.Composable

/**
 * SettingsFacade
 *
 * The single public service window ("door") for the Settings Box.
 * The rest of the app displays the settings screen via this facade.
 */
object SettingsFacade {

    @Composable
    fun SettingsModal(onDismissRequest: () -> Unit) {
        SettingsDialog(onDismissRequest = onDismissRequest)
    }
}
