package com.kairo.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Backwards compatibility delegate for [com.kairo.app.feature.tasks.location.LocationPickerDialog].
 * Location feature UI is now encapsulated in `feature.tasks.location`.
 */
@Composable
fun LocationPickerDialog(
    initialLocation: String? = null,
    initialLat: Double? = null,
    initialLng: Double? = null,
    initialRadius: Int = 500,
    onDismissRequest: () -> Unit,
    onLocationConfirmed: (name: String?, lat: Double?, lng: Double?, radius: Int) -> Unit
) {
    com.kairo.app.feature.tasks.location.LocationPickerDialog(
        initialLocation = initialLocation,
        initialLat = initialLat,
        initialLng = initialLng,
        initialRadius = initialRadius,
        onDismissRequest = onDismissRequest,
        onLocationConfirmed = onLocationConfirmed
    )
}
