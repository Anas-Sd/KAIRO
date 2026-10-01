package com.kairo.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest

data class ToneItem(
    val title: String,
    val uriString: String? // null for default system alarm
)

@Composable
fun TonePickerDialog(
    initialUri: String?,
    initialTitle: String?,
    onDismiss: () -> Unit,
    onConfirm: (uri: String?, title: String) -> Unit
) {
    val context = LocalContext.current
    var availableTones by remember { mutableStateOf<List<ToneItem>>(emptyList()) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedTitle by remember { mutableStateOf(initialTitle ?: "Default Alarm Tone") }
    var currentlyPlayingUri by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentlyPlayingUri = null
    }

    fun playAudio(uriStr: String?) {
        stopAudio()
        try {
            val audioUri = if (!uriStr.isNullOrBlank()) {
                Uri.parse(uriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            val player = MediaPlayer().apply {
                setDataSource(context, audioUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                setOnCompletionListener {
                    currentlyPlayingUri = null
                }
                prepare()
                start()
            }
            mediaPlayer = player
            currentlyPlayingUri = uriStr ?: "default"
        } catch (e: Exception) {
            Log.e("TonePickerDialog", "Failed playing audio tone", e)
            currentlyPlayingUri = null
        }
    }

    // Load available alarm tones and ringtones from system
    LaunchedEffect(Unit) {
        val list = mutableListOf<ToneItem>()
        list.add(ToneItem("Default Alarm Tone", null))

        try {
            val ringtoneManager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            }
            val cursor = ringtoneManager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = ringtoneManager.getRingtoneUri(cursor.position)
                if (uri != null) {
                    list.add(ToneItem(title = title, uriString = uri.toString()))
                }
            }
        } catch (e: Exception) {
            Log.e("TonePickerDialog", "Error fetching tones", e)
        }
        availableTones = list
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
        }
    }

    Dialog(onDismissRequest = {
        stopAudio()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = KairoSurfaceContainerLowest,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(KairoPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = KairoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Select Alarm Tone",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Tap a tone to preview audio. Selected tone will ring for this task's alarm.",
                    fontSize = 12.sp,
                    color = Color(0xFFC8C4D9),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // List of tones
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableTones) { tone ->
                        val isSelected = (tone.uriString == selectedUri) ||
                                (tone.uriString == null && selectedUri == null)
                        val isPlaying = currentlyPlayingUri == (tone.uriString ?: "default")

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) KairoPrimary.copy(alpha = 0.15f) else KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUri = tone.uriString
                                    selectedTitle = tone.title
                                    playAudio(tone.uriString)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isPlaying) KairoSecondary else if (isSelected) KairoPrimary else Color(0xFF918EA2),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = tone.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFFE3E1EC),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                stopAudio()
                                            } else {
                                                selectedUri = tone.uriString
                                                selectedTitle = tone.title
                                                playAudio(tone.uriString)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Stop" else "Preview",
                                            tint = if (isPlaying) KairoSecondary else KairoPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = KairoPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Cancel & Set Tone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            stopAudio()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text("Cancel", color = Color(0xFFC8C4D9))
                    }

                    Button(
                        onClick = {
                            stopAudio()
                            onConfirm(selectedUri, selectedTitle)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary)
                    ) {
                        Text("Confirm Tone", color = Color(0xFF130067), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
