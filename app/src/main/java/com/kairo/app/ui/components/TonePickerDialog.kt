package com.kairo.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Backward compatibility delegate for [com.kairo.app.feature.tasks.alarm.TonePickerDialog].
 * Alarm tone picker UI is now encapsulated in `feature.tasks.alarm`.
 */
@Composable
fun TonePickerDialog(
    initialUri: String?,
    initialTitle: String?,
    onDismiss: () -> Unit,
    onConfirm: (uri: String?, title: String) -> Unit
) {
    com.kairo.app.feature.tasks.alarm.TonePickerDialog(
        initialUri = initialUri,
        initialTitle = initialTitle,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
