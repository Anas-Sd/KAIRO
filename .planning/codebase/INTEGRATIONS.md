# INTEGRATIONS.md — KAIRO External Integrations
<!-- last_mapped: 2026-10-04 -->

## 1. Supabase (Primary Cloud Backend)

**Purpose:** Remote task storage, user authentication via access codes, cross-device sync.

**Location:** `app/src/main/java/com/kairo/app/feature/sync/SupabaseClient.kt`

**Endpoint:** `https://lswzgwzbvuiolkzvyrof.supabase.co`

**Auth:** Anon JWT key (`apikey` + `Authorization: Bearer` headers)

**Operations:**
- `getTasks(userCode)` — GET /rest/v1/tasks filtered by user_code
- `insertTask(taskDto)` — POST /rest/v1/tasks (merge-duplicates)
- `updateTask(taskDto)` — PATCH /rest/v1/tasks?id=eq.{id}
- `deleteTask(taskId)` — DELETE /rest/v1/tasks?id=eq.{id}
- `validateAccessCode(code)` — GET /rest/v1/access_codes?code=eq.{code}
- `rotateAccessCode(oldCode, newCode)` — PATCH /rest/v1/access_codes
- `deleteAllUserData(userCode)` — DELETE /rest/v1/tasks?user_code=eq.{code}
- `deleteUserCodeAndData(userCode)` — cascading DELETE

**HTTP client:** OkHttp 4.12.0 (15s connect / read / write timeouts)

**Fallback:** If user_code column missing, falls back to fetching all tasks without filter.

**Compatibility shim:** `data/remote/SupabaseClient.kt` forwards all calls to `feature/sync/SupabaseClient.kt`.

---

## 2. Groq API (Primary AI Provider)

**Purpose:** Ultra-low-latency LLM chat (~500 tok/s) for the AI assistant.

**Location:** `app/src/main/java/com/kairo/app/feature/ai/AiEngine.kt`

**Endpoint:** Groq REST API

**Models:**
- `llama-3.3-70b-versatile` — primary (high quality, ultra-fast)
- `llama-3.1-8b-instant` — lightweight fallback

**Auth:** API key stored in SharedPreferences (`kairo_ai_prefs` ? `groq_api_key`), with an in-app encoded default key.

**HTTP client:** OkHttp (8s connect / 12s read timeout)

---

## 3. Google Gemini API (Vision + Fallback LLM)

**Purpose:** Multimodal requests (image + text), secondary LLM fallback when Groq fails or reaches quota.

**Location:** `app/src/main/java/com/kairo/app/feature/ai/AiEngine.kt` and `AiClient.kt`

**Endpoints:**
- `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent`
- `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent` (AiEngine)

**Auth:** API key stored in SharedPreferences (`gemini_api_key`), encoded default in `AiEngine.DEFAULT_GEMINI_ENCODED` + `AiClient.DEFAULT_ENCODED_KEY`.

**Max tokens:** 120 (AiClient short-form) / configurable (AiEngine chat)

---

## 4. Google Play Services — Location & Geofencing

**Purpose:** Hardware-level geofences for location-triggered task reminders.

**Location:** `app/src/main/java/com/kairo/app/feature/tasks/location/GeofenceManager.kt`

**API:** `com.google.android.gms.location.GeofencingClient` (via `LocationServices.getGeofencingClient()`)

**Integration pattern:**
- Geofences registered on task creation/update if lat/lng are set
- `GeofenceBroadcastReceiver` handles ENTER/EXIT events
- Delivers in-app notification + potentially triggers AI notification via `LocationAiNotifier`
- Synced on app start and permission grant in `MainActivity`

**Required permissions:** ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION

**Geofence params:** Task `latitude`, `longitude`, `locationRadius` (default 500m)

---

## 5. Android SpeechRecognizer (STT)

**Purpose:** Real-time voice input for the AI assistant; wake-phrase detection.

**Location:** `app/src/main/java/com/kairo/app/feature/ai/AiVoiceManager.kt`

**API:** `android.speech.SpeechRecognizer` + `RecognizerIntent`

**Features:**
- Partial results streaming (live transcription preview)
- Automatic turn-taking voice loop (no disconnect mid-conversation)
- "Hey Buddy" wake-phrase listener (foreground, when mic permission granted)
- Starts/stops listening based on screen route (stops on AiAssistant screen, starts on others)

**Required permission:** RECORD_AUDIO

---

## 6. Android TextToSpeech (TTS)

**Purpose:** AI assistant speaks responses aloud for voice-to-voice conversation mode.

**Location:** `app/src/main/java/com/kairo/app/feature/ai/AiVoiceManager.kt`

**API:** `android.speech.tts.TextToSpeech`

**Init:** Eager singleton init at `KairoApplication` launch.

---

## 7. AlarmManager (Exact Alarms)

**Purpose:** Schedule task reminders as exact alarms (fires even in Doze mode).

**Location:** `app/src/main/java/com/kairo/app/feature/tasks/alarm/AlarmScheduler.kt`

**API:** `android.app.AlarmManager.setExactAndAllowWhileIdle()`

**Required permissions:** SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, WAKE_LOCK, VIBRATE, POST_NOTIFICATIONS

**Broadcast actions:**
- `ACTION_TRIGGER_ALARM` — fires alarm
- `ACTION_COMPLETE_ALARM` — mark task done from notification
- `ACTION_SNOOZE_ALARM` — snooze from notification
- `ACTION_DISMISS_ALARM` — dismiss from notification

**Receiver:** `AlarmReceiver` (both `feature.tasks.alarm` and `alarm` package — legacy + active)

---

## 8. Android Connectivity Manager

**Purpose:** Network state detection for online/offline sync decisions.

**Location:** `app/src/main/java/com/kairo/app/feature/sync/SyncManager.kt`

**API:** `android.net.ConnectivityManager` with `NetworkCallback`

**Pattern:** Registers a `NetworkCallback`, updates `isOnline: StateFlow<Boolean>`, triggers sync queue flush on reconnect.

---

## Integration Summary Table

| Integration | Type | Library/API | Purpose |
|---|---|---|---|
| Supabase | REST/HTTP | OkHttp | Cloud task sync + auth |
| Groq | REST/HTTP | OkHttp | Primary AI LLM |
| Gemini | REST/HTTP | OkHttp | Vision AI + LLM fallback |
| Play Services Location | SDK | gms:play-services-location | Geofencing |
| SpeechRecognizer | Android API | android.speech | Voice STT |
| TextToSpeech | Android API | android.speech.tts | AI TTS |
| AlarmManager | Android API | android.app | Task reminders |
| ConnectivityManager | Android API | android.net | Offline detection |
