package com.kairo.app.ui.components

import androidx.compose.runtime.Composable
import com.kairo.app.feature.settings.SettingsFacade

/**
 * Backward compatibility delegate for [com.kairo.app.feature.settings.SettingsFacade].
 * Settings now lives in its dedicated feature box `com.kairo.app.feature.settings`.
 */
@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit
) {
    SettingsFacade.SettingsModal(onDismissRequest = onDismissRequest)
}
