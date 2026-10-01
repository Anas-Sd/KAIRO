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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.absoluteValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.window.DialogProperties
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoSurfaceContainerLowest

private val TealActive = Color(0xFF26E0BA)
private val CoralMusicIcon = Color(0xFFE84A5F)

data class ToneItem(
    val title: String,
    val subtitle: String? = null,
    val uriString: String?, // null = system default, "NONE" = silent
    val isDeviceAudio: Boolean = false
)

private object RecentTonesManager {
    private const val PREFS_NAME = "kairo_tone_prefs"
    private const val KEY_RECENT = "recent_tones_v2"

    fun getRecentTones(context: Context): List<ToneItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split(";;").mapNotNull { entry ->
            val parts = entry.split("||")
            if (parts.size >= 2) {
                val title = parts[0]
                val uri = parts[1].takeIf { it.isNotBlank() }
                val sub = if (parts.size >= 3) parts[2] else null
                ToneItem(title = title, subtitle = sub, uriString = uri, isDeviceAudio = true)
            } else null
        }
    }

    fun saveRecentTone(context: Context, tone: ToneItem) {
        if (tone.uriString == null || tone.uriString == "NONE") return
        val current = getRecentTones(context).filter { it.uriString != tone.uriString }
        val updated = listOf(tone) + current
        val serialized = updated.take(5).joinToString(";;") { item ->
            "${item.title}||${item.uriString.orEmpty()}||${item.subtitle.orEmpty()}"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT, serialized)
            .apply()
    }

    fun removeRecentTone(context: Context, uriString: String?) {
        if (uriString == null) return
        val current = getRecentTones(context).filter { it.uriString != uriString }
        val serialized = current.joinToString(";;") { item ->
            "${item.title}||${item.uriString.orEmpty()}||${item.subtitle.orEmpty()}"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT, serialized)
            .apply()
    }
}

private fun cacheToneLocally(context: Context, uriStr: String?): String? {
    if (uriStr.isNullOrBlank() || uriStr == "NONE") return uriStr
    val uri = try {
        Uri.parse(uriStr)
    } catch (_: Exception) {
        return uriStr
    }
    if (uri.scheme == "file") return uriStr
    if (uri.scheme != "content") return uriStr

    return try {
        val tonesDir = File(context.filesDir, "custom_tones").apply { mkdirs() }
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(uriStr.toByteArray()).joinToString("") { "%02x".format(it) }
        val targetFile = File(tonesDir, "tone_$hash.mp3")

        if (!targetFile.exists() || targetFile.length() == 0L) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        if (targetFile.exists() && targetFile.length() > 0L) {
            Uri.fromFile(targetFile).toString()
        } else {
            uriStr
        }
    } catch (e: Exception) {
        Log.e("TonePickerDialog", "Failed caching tone locally: $uriStr", e)
        uriStr
    }
}

private enum class TonePickerView {
    MAIN,
    ON_DEVICE
}

@Composable
fun TonePickerDialog(
    initialUri: String?,
    initialTitle: String?,
    onDismiss: () -> Unit,
    onConfirm: (uri: String?, title: String) -> Unit
) {
    val context = LocalContext.current
    var currentView by remember { mutableStateOf(TonePickerView.MAIN) }

    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedTitle by remember { mutableStateOf(initialTitle ?: "Default") }
    var defaultToneName by remember { mutableStateOf("Default") }

    val recentTones = remember { mutableStateListOf<ToneItem>() }
    val deviceMusicTones = remember { mutableStateListOf<ToneItem>() }
    val deviceRecordingTones = remember { mutableStateListOf<ToneItem>() }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentlyPlayingUri by remember { mutableStateOf<String?>(null) }

    // Search and tab state on "On this device" screen
    var onDeviceTab by remember { mutableIntStateOf(0) } // 0: Music, 1: Recordings
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

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
        if (uriStr == "NONE") return // Silent mode

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

    // SAF Picker to choose any audio file directly from device storage
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
            val cachedUri = cacheToneLocally(context, it.toString()) ?: it.toString()
            val songName = name ?: "Custom Audio File"
            val newItem = ToneItem(
                title = songName,
                subtitle = "Custom Audio",
                uriString = cachedUri,
                isDeviceAudio = true
            )
            deviceMusicTones.add(0, newItem)
            selectedUri = cachedUri
            selectedTitle = songName
            RecentTonesManager.saveRecentTone(context, newItem)
            recentTones.clear()
            recentTones.addAll(RecentTonesManager.getRecentTones(context))
            playAudio(cachedUri)
        }
    }

    // Load initial defaults, recent tones & device songs
    LaunchedEffect(Unit) {
        // Query system default alarm tone name (e.g. "Delight")
        try {
            val defUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(context, defUri)
            val name = ringtone?.getTitle(context)
            if (!name.isNullOrBlank()) {
                defaultToneName = name
            }
        } catch (_: Exception) {}

        // Load recently used tones from SharedPreferences
        recentTones.clear()
        val loadedRecents = RecentTonesManager.getRecentTones(context)
        recentTones.addAll(loadedRecents)

        // Query MediaStore Audio files (Downloaded songs & Recordings)
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA
            )
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = it.getString(titleCol) ?: it.getString(nameCol) ?: "Audio Track"
                    val artist = it.getString(artistCol)?.takeIf { a -> a != "<unknown>" }
                    val path = if (dataCol != -1) it.getString(dataCol).orEmpty() else ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    val isRecording = path.contains("recording", ignoreCase = true) ||
                            path.contains("voice", ignoreCase = true) ||
                            path.contains("WhatsApp", ignoreCase = true) ||
                            rawTitle.startsWith("AUD-", ignoreCase = true)

                    val item = ToneItem(
                        title = rawTitle,
                        subtitle = artist ?: if (isRecording) "Voice recording" else "Downloaded song",
                        uriString = contentUri.toString(),
                        isDeviceAudio = true
                    )

                    if (isRecording) {
                        deviceRecordingTones.add(item)
                    } else {
                        deviceMusicTones.add(item)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TonePickerDialog", "Error querying device media", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
        }
    }

    Dialog(
        onDismissRequest = {
            stopAudio()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = KairoSurfaceContainerLowest,
            border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
        ) {
            if (currentView == TonePickerView.MAIN) {
                // ==========================================
                // SCREEN 1: RINGTONE MAIN (Matches img1)
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Top Bar: Back arrow + Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                stopAudio()
                                onDismiss()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Ringtone",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 1 Card: Default, None, and Recently Used
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = KairoSurfaceContainerHighest.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Row 1: Default
                                    val isDefaultSelected = (selectedUri == null)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedUri = null
                                                selectedTitle = defaultToneName
                                                playAudio(null)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Default",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = defaultToneName,
                                                fontSize = 13.sp,
                                                color = TealActive,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        RadioButton(
                                            selected = isDefaultSelected,
                                            onClick = {
                                                selectedUri = null
                                                selectedTitle = defaultToneName
                                                playAudio(null)
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = TealActive,
                                                unselectedColor = Color(0xFF6B687C)
                                            )
                                        )
                                    }

                                    HorizontalDivider(
                                        color = KairoOutlineVariant.copy(alpha = 0.2f),
                                        thickness = 1.dp
                                    )

                                    // Row 2: None (Silent)
                                    val isNoneSelected = (selectedUri == "NONE")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedUri = "NONE"
                                                selectedTitle = "None"
                                                stopAudio()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "None",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        RadioButton(
                                            selected = isNoneSelected,
                                            onClick = {
                                                selectedUri = "NONE"
                                                selectedTitle = "None"
                                                stopAudio()
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = TealActive,
                                                unselectedColor = Color(0xFF6B687C)
                                            )
                                        )
                                    }

                                    // Row 3+: Recently Used Tones
                                    if (recentTones.isNotEmpty()) {
                                        recentTones.forEach { recent ->
                                            HorizontalDivider(
                                                color = KairoOutlineVariant.copy(alpha = 0.2f),
                                                thickness = 1.dp
                                            )
                                            val isRecentSelected = (selectedUri == recent.uriString)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedUri = recent.uriString
                                                        selectedTitle = recent.title
                                                        playAudio(recent.uriString)
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = recent.title,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (isRecentSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = recent.subtitle ?: "Recently used",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF918EA2),
                                                        maxLines = 1
                                                    )
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Small 'x' mark to remove recent tone
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .background(Color(0xFF2C293A).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                RecentTonesManager.removeRecentTone(context, recent.uriString)
                                                                recentTones.clear()
                                                                recentTones.addAll(RecentTonesManager.getRecentTones(context))
                                                                if (selectedUri == recent.uriString) {
                                                                    stopAudio()
                                                                    selectedUri = null
                                                                    selectedTitle = defaultToneName
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove recent tone",
                                                            tint = Color(0xFFC8C4D9),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }

                                                    RadioButton(
                                                        selected = isRecentSelected,
                                                        onClick = {
                                                            selectedUri = recent.uriString
                                                            selectedTitle = recent.title
                                                            playAudio(recent.uriString)
                                                        },
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = TealActive,
                                                            unselectedColor = Color(0xFF6B687C)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Custom -> "On this device"
                        item {
                            Text(
                                text = "Custom",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF918EA2),
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = KairoSurfaceContainerHighest.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, KairoOutlineVariant.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        stopAudio()
                                        currentView = TonePickerView.ON_DEVICE
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "On this device",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open",
                                        tint = Color(0xFF918EA2),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action buttons
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
                                val finalUri = cacheToneLocally(context, selectedUri)
                                onConfirm(finalUri, selectedTitle)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KairoPrimary)
                        ) {
                            Text("Confirm", color = Color(0xFF130067), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // ==========================================
                // SCREEN 2: ON THIS DEVICE (Matches img2)
                // ==========================================
                val activeList = if (onDeviceTab == 0) deviceMusicTones else deviceRecordingTones
                val filteredList = if (searchQuery.isBlank()) {
                    activeList
                } else {
                    activeList.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                (it.subtitle?.contains(searchQuery, ignoreCase = true) == true)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Top Bar: Back arrow + Title + Search icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    stopAudio()
                                    currentView = TonePickerView.MAIN
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = "On this device",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }
                    }

                    if (isSearchActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search songs or recordings...", color = Color(0xFF6B687C), fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealActive,
                                unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.4f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.35f),
                                unfocusedContainerColor = KairoSurfaceContainerHighest.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tabs: Music vs Recordings
                    TabRow(
                        selectedTabIndex = onDeviceTab,
                        containerColor = Color.Transparent,
                        contentColor = TealActive,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[onDeviceTab]),
                                color = TealActive
                            )
                        }
                    ) {
                        Tab(
                            selected = onDeviceTab == 0,
                            onClick = { onDeviceTab = 0 },
                            text = {
                                Text(
                                    "Music",
                                    fontWeight = if (onDeviceTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (onDeviceTab == 0) Color.White else Color(0xFF918EA2)
                                )
                            }
                        )
                        Tab(
                            selected = onDeviceTab == 1,
                            onClick = { onDeviceTab = 1 },
                            text = {
                                Text(
                                    "Recordings",
                                    fontWeight = if (onDeviceTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (onDeviceTab == 1) Color.White else Color(0xFF918EA2)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Direct file picker chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = KairoSurfaceContainerHighest.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, TealActive.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pickAudioLauncher.launch("audio/*") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TealActive,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Select audio from files / storage...",
                                fontSize = 12.sp,
                                color = TealActive,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // List of Audio Tracks
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filteredList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No audio found matching \"$searchQuery\""
                                        else "No audio files detected on device",
                                        color = Color(0xFF6B687C),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            items(filteredList) { track ->
                                val isSelected = (selectedUri == track.uriString || selectedTitle == track.title)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) TealActive.copy(alpha = 0.1f) else KairoSurfaceContainerHighest.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val cachedUri = cacheToneLocally(context, track.uriString) ?: track.uriString
                                            val cachedTrack = track.copy(uriString = cachedUri)
                                            selectedUri = cachedUri
                                            selectedTitle = track.title
                                            RecentTonesManager.saveRecentTone(context, cachedTrack)
                                            recentTones.clear()
                                            recentTones.addAll(RecentTonesManager.getRecentTones(context))
                                            playAudio(cachedUri)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Red coral music icon container (Matches img2)
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(CoralMusicIcon, RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (track.subtitle != null) {
                                                    Text(
                                                        text = track.subtitle,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF918EA2),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                val cachedUri = cacheToneLocally(context, track.uriString) ?: track.uriString
                                                val cachedTrack = track.copy(uriString = cachedUri)
                                                selectedUri = cachedUri
                                                selectedTitle = track.title
                                                RecentTonesManager.saveRecentTone(context, cachedTrack)
                                                recentTones.clear()
                                                recentTones.addAll(RecentTonesManager.getRecentTones(context))
                                                playAudio(cachedUri)
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = TealActive,
                                                unselectedColor = Color(0xFF6B687C)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Done selecting on device
                    Button(
                        onClick = {
                            stopAudio()
                            currentView = TonePickerView.MAIN
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealActive)
                    ) {
                        Text("Done", color = Color(0xFF0F0E13), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
