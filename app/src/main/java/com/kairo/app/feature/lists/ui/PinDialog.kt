package com.kairo.app.feature.lists.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh

enum class PinDialogMode {
    SET_PIN,
    UNLOCK_TEMPORARY,
    REMOVE_PIN
}

@Composable
fun ListPinDialog(
    listName: String,
    mode: PinDialogMode,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val title = when (mode) {
        PinDialogMode.SET_PIN -> "Lock List"
        PinDialogMode.UNLOCK_TEMPORARY -> "Unlock List"
        PinDialogMode.REMOVE_PIN -> "Permanently Unlock"
    }

    val subtitle = when (mode) {
        PinDialogMode.SET_PIN -> "Enter a 4-digit PIN to protect \"$listName\" and hide its subtasks."
        PinDialogMode.UNLOCK_TEMPORARY -> "Enter the PIN to open and view \"$listName\"."
        PinDialogMode.REMOVE_PIN -> "Enter the current PIN to permanently remove protection from \"$listName\"."
    }

    val confirmButtonText = when (mode) {
        PinDialogMode.SET_PIN -> "Lock List"
        PinDialogMode.UNLOCK_TEMPORARY -> "Unlock"
        PinDialogMode.REMOVE_PIN -> "Remove Lock"
    }

    val handleValidateAndSubmit = {
        errorMessage = null
        if (pin.length < 4) {
            errorMessage = "PIN must be at least 4 digits"
        } else if (mode == PinDialogMode.SET_PIN && pin != confirmPin) {
            errorMessage = "PINs do not match. Please re-enter."
        } else {
            val success = onConfirm(pin)
            if (!success) {
                errorMessage = if (mode == PinDialogMode.SET_PIN) {
                    "Failed to set PIN. Please try again."
                } else {
                    "Incorrect PIN. Please try again."
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = KairoSurfaceContainerHigh,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row with Icon and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (mode == PinDialogMode.REMOVE_PIN) KairoHighUrgent.copy(alpha = 0.12f)
                                else KairoPrimary.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mode == PinDialogMode.REMOVE_PIN) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (mode == PinDialogMode.REMOVE_PIN) KairoHighUrgent else KairoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title & Subtitle
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // PIN Input
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(6)
                        pin = digits
                        errorMessage = null
                    },
                    label = { Text(if (mode == PinDialogMode.SET_PIN) "Enter 4-digit PIN" else "PIN") },
                    placeholder = { Text("••••") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = if (mode == PinDialogMode.SET_PIN) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { handleValidateAndSubmit() }
                    ),
                    singleLine = true,
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.4f),
                        errorBorderColor = KairoHighUrgent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                // Confirm PIN Input (if setting PIN)
                if (mode == PinDialogMode.SET_PIN) {
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(6)
                            confirmPin = digits
                            errorMessage = null
                        },
                        label = { Text("Confirm PIN") },
                        placeholder = { Text("••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { handleValidateAndSubmit() }
                        ),
                        singleLine = true,
                        isError = errorMessage != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KairoPrimary,
                            unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.4f),
                            errorBorderColor = KairoHighUrgent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Error Message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = KairoHighUrgent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { handleValidateAndSubmit() },
                        enabled = pin.length >= 4 && (mode != PinDialogMode.SET_PIN || confirmPin.length >= 4),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == PinDialogMode.REMOVE_PIN) KairoHighUrgent else KairoPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(
                            text = confirmButtonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
