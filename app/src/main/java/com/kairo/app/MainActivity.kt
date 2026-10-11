package com.kairo.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kairo.app.feature.auth.AuthFeatureFacade
import com.kairo.app.ui.MainScreen
import com.kairo.app.ui.theme.KAIROTheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.tasks.alarm.FullScreenAlarmPermissionManager
import com.kairo.app.ui.theme.KairoPrimary

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            try {
                com.kairo.app.feature.tasks.location.TaskLocationFacade.syncGeofencesWithActiveTasks(this)
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Error syncing geofences: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestAppPermissions()

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                com.kairo.app.feature.tasks.location.TaskLocationFacade.syncGeofencesWithActiveTasks(this)
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error syncing geofences: ${e.message}", e)
        }

        setContent {
            KAIROTheme {
                val session by AuthFeatureFacade.sessionState.collectAsState()
                var showOverlayPrompt by remember {
                    mutableStateOf(!FullScreenAlarmPermissionManager.hasOverlayPermission(this@MainActivity))
                }

                if (session == null) {
                    AuthFeatureFacade.LoginView()
                } else {
                    MainScreen()
                }

                if (showOverlayPrompt) {
                    AlertDialog(
                        onDismissRequest = { showOverlayPrompt = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = KairoPrimary
                            )
                        },
                        title = {
                            Text(
                                text = "Direct Full-Screen Alarms",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                text = "To show alarms directly in full-screen over other apps and when your phone is asleep (without a notification banner), please allow 'Display over other apps'.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showOverlayPrompt = false
                                    FullScreenAlarmPermissionManager.openOverlaySettings(this@MainActivity)
                                    if (FullScreenAlarmPermissionManager.isXiaomiDevice()) {
                                        FullScreenAlarmPermissionManager.openXiaomiPermissions(this@MainActivity)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary)
                            ) {
                                Text("Allow Now")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showOverlayPrompt = false }) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Location Permissions for Hardware Geofences
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}

