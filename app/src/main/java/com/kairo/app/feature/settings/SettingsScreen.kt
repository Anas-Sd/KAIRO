package com.kairo.app.feature.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.window.DialogProperties
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.local.OfflineTaskItem
import com.kairo.app.data.remote.SupabaseClient
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.sync.SyncManager
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoCardSurface
import com.kairo.app.ui.theme.KairoHighUrgent
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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

    // Section collapse states (side headings)
    var isSyncExpanded by remember { mutableStateOf(true) }
    var isAccountExpanded by remember { mutableStateOf(true) }
    var isDataExpanded by remember { mutableStateOf(false) }
    var isSessionExpanded by remember { mutableStateOf(false) }

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

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = KairoBackground
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        color = KairoSurfaceContainer,
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "Settings",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            )

                            // Network Status Pill
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (isOnline) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isOnline) "Online" else "Offline",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = KairoBackground
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // ==========================================
                    // 1. COLLAPSIBLE SECTION: OFFLINE TASKS & SYNC
                    // ==========================================
                    CollapsibleSettingsSection(
                        title = "Offline Tasks & Cloud Sync",
                        subtitle = if (pendingCount > 0) "$pendingCount offline change(s) waiting for sync" else "Database is synchronized",
                        icon = Icons.Default.CloudSync,
                        iconTint = if (pendingCount > 0) Color(0xFFF59E0B) else KairoSecondary,
                        badgeText = if (pendingCount > 0) "$pendingCount Pending" else "All Synced",
                        badgeColor = if (pendingCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
                        isExpanded = isSyncExpanded,
                        onToggle = { isSyncExpanded = !isSyncExpanded }
                    ) {
                        // Cloud sync control card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, KairoOutlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isOnline) "Connected to Database" else "Offline Mode Active",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isOnline) "Sync updates directly with Supabase" else "Changes saved safely on this device",
                                    fontSize = 11.sp,
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
                                            Toast.makeText(
                                                context,
                                                if (count > 0) "Successfully synced $count tasks with database!" else "Database is already fully up to date",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            if (!isOnline) {
                                                Toast.makeText(context, "Device offline. Tasks stored safely in local database.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Sync check completed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Real-time Pending Offline Tasks List
                        if (offlineTasks.isEmpty()) {
                            // Empty state: All tasks synced
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "All Tasks Synced",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                        Text(
                                            text = "No offline changes pending. All tasks are present and up to date in the cloud database.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            // Tasks pending sync list
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TASKS WAITING TO SYNC (${offlineTasks.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "Auto-clears once synced",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                offlineTasks.forEach { item ->
                                    OfflineTaskCard(item = item)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 2. COLLAPSIBLE SECTION: ACCOUNT & ACCESS CODE
                    // ==========================================
                    CollapsibleSettingsSection(
                        title = "Account & Workspace",
                        subtitle = "$userName • Workspace Code",
                        icon = Icons.Default.Person,
                        iconTint = KairoPrimary,
                        isExpanded = isAccountExpanded,
                        onToggle = { isAccountExpanded = !isAccountExpanded }
                    ) {
                        // Profile Info Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(KairoPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userName.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = userName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Personal Workspace",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Confidential Access Code Box
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
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, KairoOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
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

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingsActionRow(
                            icon = Icons.Default.SyncLock,
                            iconTint = KairoPrimary,
                            title = "Rotate Access Code",
                            subtitle = "Update code in database & re-login",
                            onClick = {
                                newRotateCode = ""
                                rotateError = null
                                showRotateDialog = true
                            }
                        )
                    }

                    // ==========================================
                    // 3. COLLAPSIBLE SECTION: DATA MANAGEMENT
                    // ==========================================
                    CollapsibleSettingsSection(
                        title = "Data Management",
                        subtitle = "Database wipe and reset options",
                        icon = Icons.Default.Storage,
                        iconTint = Color(0xFFF59E0B),
                        isExpanded = isDataExpanded,
                        onToggle = { isDataExpanded = !isDataExpanded }
                    ) {
                        SettingsActionRow(
                            icon = Icons.Default.DeleteSweep,
                            iconTint = Color(0xFFF59E0B),
                            title = "Delete All Data",
                            subtitle = "Clear all tasks from database while keeping your access code",
                            onClick = { showDeleteDataDialog = true }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        SettingsActionRow(
                            icon = Icons.Default.DeleteForever,
                            iconTint = KairoHighUrgent,
                            title = "Delete Complete Code",
                            subtitle = "Permanently delete access code & all tasks from database",
                            onClick = { showDeleteCompleteDialog = true }
                        )
                    }

                    // ==========================================
                    // 4. COLLAPSIBLE SECTION: SESSION
                    // ==========================================
                    CollapsibleSettingsSection(
                        title = "Session",
                        subtitle = "Active device session",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        isExpanded = isSessionExpanded,
                        onToggle = { isSessionExpanded = !isSessionExpanded }
                    ) {
                        SettingsActionRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = "Log Out",
                            subtitle = "Sign out from this device safely",
                            onClick = { showLogoutDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
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

/**
 * Reusable Collapsible Section Card with side heading, icon, badge, chevron animation, and expand/collapse body
 */
@Composable
private fun CollapsibleSettingsSection(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = KairoPrimary,
    badgeText: String? = null,
    badgeColor: Color = KairoPrimary,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "collapse_chevron"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = KairoCardSurface,
        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable to collapse/expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotationState)
                )
            }

            // Collapsible Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)
                ) {
                    HorizontalDivider(
                        color = KairoOutlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    content()
                }
            }
        }
    }
}

/**
 * Offline Task Card showing unsynced task details
 */
@Composable
private fun OfflineTaskCard(item: OfflineTaskItem) {
    val (badgeBg, badgeFg, badgeText) = when {
        item.action.contains("Created", ignoreCase = true) -> Triple(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981), "NEW")
        item.action.contains("Updated", ignoreCase = true) -> Triple(Color(0xFF38BDF8).copy(alpha = 0.15f), Color(0xFF38BDF8), "EDIT")
        item.action.contains("Deleted", ignoreCase = true) -> Triple(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFEF4444), "DELETE")
        else -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "PENDING")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, KairoOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val timeFormat = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
            val timeStr = timeFormat.format(Date(item.timestamp))

            Text(
                text = "${item.action} • $timeStr",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!item.location.isNullOrBlank()) {
                Text(
                    text = "Location: ${item.location}",
                    fontSize = 10.sp,
                    color = KairoPrimary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

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
