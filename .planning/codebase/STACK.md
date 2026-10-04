# STACK.md — KAIRO Technology Stack
<!-- last_mapped: 2026-10-04 -->

## Runtime & Platform

| Layer | Choice |
|---|---|
| Platform | Native Android (minSdk 26 / API 8.0+, targetSdk 35) |
| Language | Kotlin 2.0.20 |
| UI Framework | Jetpack Compose + Material 3 |
| Compose BOM | 2024.09.02 |
| Build System | Gradle 8 (Kotlin DSL .kts) + Version Catalogs (libs.versions.toml) |
| JVM Target | Java 21 |
| AGP | 8.5.2 |

## Core Dependencies

| Library | Version | Purpose |
|---|---|---|
| androidx.core:core-ktx | 1.13.1 | Android KTX extensions |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.6 | Coroutine lifecycle scope |
| androidx.activity:activity-compose | 1.9.2 | Compose Activity integration |
| androidx.navigation:navigation-compose | 2.8.1 | In-app navigation |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.6 | ViewModel in Compose |
| androidx.compose.material3 | (BOM) | Material Design 3 |
| androidx.compose.material:material-icons-extended | 1.7.2 | Extended icon pack |
| com.squareup.okhttp3:okhttp | 4.12.0 | HTTP client for API calls |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.7.3 | JSON serialization |
| com.google.android.gms:play-services-location | 21.3.0 | FusedLocation + Geofencing API |
| androidx.fragment:fragment-ktx | 1.8.5 | Fragment interop |

## Testing Stack

| Library | Version | Use |
|---|---|---|
| junit:junit | 4.13.2 | Unit tests |
| androidx.test.ext:junit | 1.2.1 | Android JUnit runner |
| androidx.test.espresso:espresso-core | 3.6.1 | UI tests |
| androidx.compose.ui:ui-test-junit4 | (BOM) | Compose UI tests |

## State Management

- StateFlow + MutableStateFlow — primary reactive state container
- Kotlin Coroutines — viewModelScope, CoroutineScope(SupervisorJob() + Dispatchers.IO)
- Flow — for reactive streams (tasks, undoAction, isOnline, isSyncing)
- No Hilt/Dagger — manual DI via object singletons + constructor injection

## Local Storage

- SQLite via SQLiteOpenHelper — LocalTaskDatabase (kairo_local_tasks.db, version 2)
  - local_tasks table — full task schema with 25+ columns
  - sync_queue table — offline pending operations (INSERT/UPDATE/DELETE)
- SharedPreferences — auth session (kairo_auth_prefs), AI prefs (kairo_ai_prefs), AI memory (kairo_ai_memory)

## AI / LLM Stack

| Provider | Model | Role |
|---|---|---|
| Groq | llama-3.3-70b-versatile | Primary (~500 tok/s, ultra-low latency) |
| Google Gemini | gemini-1.5-flash / 2.5-flash | Multimodal Vision + fallback |
| Groq | llama-3.1-8b-instant | High-throughput lightweight fallback |
| Local Rule Engine | — | 100% offline fallback tier |

- API keys in SharedPreferences, encoded defaults bundled in-app
- HTTP client: OkHttp (8s connect / 12s read timeout)

## Voice Stack

- Android SpeechRecognizer — real-time STT with partial transcription streaming
- Android TextToSpeech — instant spoken AI responses
- Wake-phrase: "Hey Buddy" (foreground listener when mic permission granted)

## Remote Backend

- Supabase (PostgreSQL) — cloud task sync via REST API
  - Endpoint: https://lswzgwzbvuiolkzvyrof.supabase.co
  - Auth: anon JWT bearer key
  - Tables: tasks, access_codes

## Build Configuration

- compileSdk = 35, minSdk = 26, targetSdk = 35
- versionCode = 1, versionName = "1.0.0"
- isMinifyEnabled = false (ProGuard disabled — MVP)
- Release uses debug signing config (MVP stage)

## Key Kotlin Plugins

- kotlin-android
- kotlin-compose
- kotlin-serialization (for @Serializable DTOs)
