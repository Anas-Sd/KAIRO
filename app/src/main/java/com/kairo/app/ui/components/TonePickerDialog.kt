package com.kairo.app.ui.components

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
    val subtitle: String? = null,
    val uriString: String?, // null for default system alarm
    val isDeviceAudio: Boolean = false
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
    var searchQuery by remember { mutableStateOf("") }
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

    // SAF Picker to choose any downloaded song or audio file directly
    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            var name: String? = null
            if (it.scheme == "content") {
                try {
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) name = cursor.getString(idx)
                        }
                    }
                } catch (_: Exception) {}
            }
            val songName = name ?: "Custom Audio Track"
            val newItem = ToneItem(
                title = songName,
                subtitle = "Custom Audio / Download",
                uriString = it.toString(),
                isDeviceAudio = true
            )
            availableTones = listOf(newItem) + availableTones.filter { item -> item.uriString != it.toString() }
            selectedUri = it.toString()
            selectedTitle = songName
            playAudio(it.toString())
        }
    }

    // Load downloaded audio files & system alarm tones
    LaunchedEffect(Unit) {
        val list = mutableListOf<ToneItem>()
        list.add(ToneItem("Default Alarm Tone", "System Default", null, false))

        // 1. Query Downloaded / Music tracks from MediaStore
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.TITLE} IS NOT NULL"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val trackTitle = it.getString(titleCol) ?: it.getString(nameCol) ?: "Downloaded Song"
                    val artist = it.getString(artistCol)?.takeIf { a -> a != "<unknown>" }
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(
                        ToneItem(
                            title = trackTitle,
                            subtitle = artist ?: "Downloaded Music",
                            uriString = contentUri.toString(),
                            isDeviceAudio = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TonePickerDialog", "Error querying device songs", e)
        }

        // 2. Query System Tones from RingtoneManager
        try {
            val ringtoneManager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            }
            val cursor = ringtoneManager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = ringtoneManager.getRingtoneUri(cursor.position)
                if (uri != null) {
                    list.add(
                        ToneItem(
                            title = title,
                            subtitle = "System Tone",
                            uriString = uri.toString(),
                            isDeviceAudio = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TonePickerDialog", "Error fetching system tones", e)
        }

        // If the initial tone was a custom URI not already listed, include it
        if (!initialUri.isNullOrBlank() && list.none { it.uriString == initialUri }) {
            list.add(
                1,
                ToneItem(
                    title = initialTitle ?: "Selected Tone",
                    subtitle = "Previously Selected",
                    uriString = initialUri,
                    isDeviceAudio = true
                )
            )
        }

        availableTones = list
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
        }
    }

    // Filter tones by real-time search query
    val filteredTones = remember(availableTones, searchQuery) {
        if (searchQuery.isBlank()) {
            availableTones
        } else {
            availableTones.filter { tone ->
                tone.title.contains(searchQuery, ignoreCase = true) ||
                (tone.subtitle?.contains(searchQuery, ignoreCase = true) == true)
            }
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

                    IconButton(
                        onClick = {
                            stopAudio()
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFC8C4D9)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search tones or songs...",
                            color = Color(0xFF6B687C),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF918EA2),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFFC8C4D9),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KairoPrimary,
                        unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.35f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f),
                        unfocusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option to pick any audio file from device storage
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KairoSurfaceContainerHighest.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickAudioLauncher.launch("audio/*") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Custom Audio",
                            tint = KairoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Choose downloaded file from device...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = KairoPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List of filtered tones & songs
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredTones.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No tones match \"$searchQuery\"",
                                    color = Color(0xFF6B687C),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(filteredTones) { tone ->
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
                                        Column {
                                            Text(
                                                text = tone.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFFE3E1EC),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (tone.subtitle != null) {
                                                Text(
                                                    text = tone.subtitle,
                                                    fontSize = 11.sp,
                                                    color = if (tone.isDeviceAudio) KairoSecondary.copy(alpha = 0.85f) else Color(0xFF918EA2),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
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
