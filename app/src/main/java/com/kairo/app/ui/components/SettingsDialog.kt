package com.kairo.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncLock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.remote.SupabaseClient
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.sync.SyncManager
import com.kairo.app.ui.theme.KairoCardSurface
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val session by AuthManager.sessionState.collectAsState()

    val userName = session?.name ?: "User"
    val userCode = session?.code ?: ""

    val isOnline by SyncManager.isOnline.collectAsState()
    val isSyncing by SyncManager.isSyncing.collectAsState()
    val offlineTasks by SyncManager.offlineTasks.collectAsState()
    val pendingCount by SyncManager.pendingSyncCount.collectAsState()

    LaunchedEffect(Unit) {
        SyncManager.refreshOfflineQueueStatus()
    }

    var isCodeVisible by remember { mutableStateOf(false) }
    var showRotateDialog by remember { mutableStateOf(false) }
    var showDeleteDataDialog by remember { mutableStateOf(false) }
    var showDeleteCompleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Rotate Code state
    var newRotateCode by remember { mutableStateOf("") }
    var isRotateLoading by remember { mutableStateOf(false) }
    var rotateError by remember { mutableStateOf<String?>(null) }

    // Action loading states
    var isActionLoading by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = KairoCardSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Profile Header Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(KairoPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = userName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active Workspace",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confidential Access Code Section
                Text(
                    text = "ACCESS CODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            KairoOutlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isCodeVisible) userCode else "••••••••••••",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { isCodeVisible = !isCodeVisible },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isCodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isCodeVisible) "Hide code" else "Show code",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(userCode))
                                Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // OFFLINE TASKS & SYNC SECTION
                // ==========================================
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OFFLINE TASKS & SYNC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isOnline) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isOnline) "Online" else "Offline",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (pendingCount == 0) "All tasks synchronized" else "$pendingCount task${if (pendingCount == 1) "" else "s"} stored offline",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (pendingCount == 0) "Local database is in sync with cloud DB" else "Pending database update — stored safely locally",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        val result = SyncManager.performSync(forceCheckNetwork = true)
                                        if (result.isSuccess) {
                                            val count = result.getOrDefault(0)
                                            Toast.makeText(context, if (count > 0) "Synced $count tasks with database!" else "Database is already up to date", Toast.LENGTH_SHORT).show()
                                        } else {
                                            if (!isOnline) {
                                                Toast.makeText(context, "Device offline. Tasks are safely saved locally.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Sync check completed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Syncing", fontSize = 12.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Now", fontSize = 12.sp)
                                }
                            }
                        }

                        if (offlineTasks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "PENDING OFFLINE TASKS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                offlineTasks.take(15).forEach { item ->
                                    val (badgeBg, badgeFg, badgeText) = when {
                                        item.action.contains("Created", ignoreCase = true) -> Triple(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981), "NEW")
                                        item.action.contains("Updated", ignoreCase = true) -> Triple(Color(0xFF38BDF8).copy(alpha = 0.15f), Color(0xFF38BDF8), "EDIT")
                                        item.action.contains("Deleted", ignoreCase = true) -> Triple(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFEF4444), "DELETE")
                                        else -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "OFFLINE")
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeFg,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                                            val timeStr = timeFormat.format(java.util.Date(item.timestamp))
                                            Text(
                                                text = "${item.action} • $timeStr",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = KairoOutlineVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Actions List
                SettingsActionRow(
                    icon = Icons.Default.SyncLock,
                    iconTint = KairoPrimary,
                    title = "Rotate Access Code",
                    subtitle = "Update code in database & log out automatically",
                    onClick = {
                        newRotateCode = ""
                        rotateError = null
                        showRotateDialog = true
                    }
                )

                SettingsActionRow(
                    icon = Icons.Default.DeleteSweep,
                    iconTint = Color(0xFFF59E0B), // Amber
                    title = "Delete All Data",
                    subtitle = "Clear all tasks while keeping your access code",
                    onClick = { showDeleteDataDialog = true }
                )

                SettingsActionRow(
                    icon = Icons.Default.DeleteForever,
                    iconTint = KairoHighUrgent,
                    title = "Delete Complete Code",
                    subtitle = "Permanently delete access code & all tasks",
                    onClick = { showDeleteCompleteDialog = true }
                )

                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "Log Out",
                    subtitle = "Sign out from this device",
                    onClick = { showLogoutDialog = true }
                )
            }
        }
    }

    // ==========================================
    // ROTATE CODE DIALOG
    // ==========================================
    if (showRotateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRotateLoading) showRotateDialog = false },
            title = {
                Text("Rotate Access Code", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        "Enter your new access code. Your existing tasks will be migrated to the new code, and you will be logged out automatically.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newRotateCode,
                        onValueChange = {
                            newRotateCode = it
                            rotateError = null
                        },
                        label = { Text("New Access Code") },
                        placeholder = { Text("e.g. SECURE-5678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KairoPrimary,
                            cursorColor = KairoPrimary
                        )
                    )
                    if (rotateError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = rotateError.orEmpty(),
                            color = KairoHighUrgent,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanNew = newRotateCode.trim()
                        if (cleanNew.isEmpty()) {
                            rotateError = "Please enter a new access code"
                            return@Button
                        }
                        isRotateLoading = true
                        rotateError = null
                        scope.launch {
                            SupabaseClient.rotateAccessCode(userCode, cleanNew)
                                .onSuccess {
                                    isRotateLoading = false
                                    showRotateDialog = false
                                    onDismissRequest()
                                    Toast.makeText(context, "Code rotated! Please log in with new code.", Toast.LENGTH_LONG).show()
                                    TaskRepository.clearTasksLocally()
                                    AuthManager.clearSession()
                                }
                                .onFailure { error ->
                                    isRotateLoading = false
                                    rotateError = error.localizedMessage ?: "Failed to rotate code"
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                    enabled = !isRotateLoading && newRotateCode.isNotBlank()
                ) {
                    if (isRotateLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Update & Log Out")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRotateDialog = false },
                    enabled = !isRotateLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==========================================
    // DELETE ALL DATA DIALOG
    // ==========================================
    if (showDeleteDataDialog) {
        AlertDialog(
            onDismissRequest = { if (!isActionLoading) showDeleteDataDialog = false },
            icon = {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFF59E0B))
            },
            title = {
                Text("Delete All Data?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete all tasks? This will permanently delete your tasks from the database. Your access code will remain valid.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isActionLoading = true
                        scope.launch {
                            SupabaseClient.deleteAllUserData(userCode)
                                .onSuccess {
                                    isActionLoading = false
                                    showDeleteDataDialog = false
                                    TaskRepository.clearTasksLocally()
                                    Toast.makeText(context, "All tasks deleted successfully", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { error ->
                                    isActionLoading = false
                                    Toast.makeText(context, "Failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    enabled = !isActionLoading
                ) {
                    if (isActionLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Tasks")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDataDialog = false },
                    enabled = !isActionLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==========================================
    // DELETE COMPLETE CODE DIALOG
    // ==========================================
    if (showDeleteCompleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isActionLoading) showDeleteCompleteDialog = false },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = KairoHighUrgent)
            },
            title = {
                Text("Delete Code & All Data?", fontWeight = FontWeight.Bold, color = KairoHighUrgent)
            },
            text = {
                Text(
                    "This action is permanent and cannot be undone! Your access code and all tasks will be deleted immediately. You will be logged out and will lose all access.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isActionLoading = true
                        scope.launch {
                            SupabaseClient.deleteUserCodeAndData(userCode)
                                .onSuccess {
                                    isActionLoading = false
                                    showDeleteCompleteDialog = false
                                    onDismissRequest()
                                    Toast.makeText(context, "Code and data permanently deleted", Toast.LENGTH_LONG).show()
                                    TaskRepository.clearTasksLocally()
                                    AuthManager.clearSession()
                                }
                                .onFailure { error ->
                                    isActionLoading = false
                                    Toast.makeText(context, "Failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KairoHighUrgent),
                    enabled = !isActionLoading
                ) {
                    if (isActionLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Permanently")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteCompleteDialog = false },
                    enabled = !isActionLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==========================================
    // LOGOUT DIALOG
    // ==========================================
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text("Log Out?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to log out from this device?", fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onDismissRequest()
                        TaskRepository.clearTasksLocally()
                        AuthManager.clearSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
