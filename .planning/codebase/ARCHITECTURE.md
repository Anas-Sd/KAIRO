# ARCHITECTURE.md — KAIRO System Architecture
<!-- last_mapped: 2026-10-04 -->

## Architectural Pattern

**MVVM with Unidirectional Data Flow (UDF)**

- UI ? ViewModel ? Repository ? Local DB / Remote API
- Data flows UP as StateFlow; events flow DOWN as function calls
- Single source of truth: local SQLite (kairo_local_tasks.db); Supabase is the remote mirror

## Layer Diagram

```
+---------------------------------------------------------+
¦  UI Layer (Jetpack Compose)                              ¦
¦  Screens: TasksScreen, AiAssistantScreen, LoginScreen… ¦
¦  Components: TaskCard, CreateTaskDialog, SectionHeader…  ¦
¦  Navigation: NavHost + BottomNavBar (5 tabs + AI)        ¦
+---------------------------------------------------------+
                  ¦ State (StateFlow) / Events (lambdas)
+-----------------?---------------------------------------+
¦  ViewModel Layer                                         ¦
¦  TaskViewModel — owns TasksUiState + UiToggles           ¦
¦  (Single shared ViewModel for the Tasks screen)          ¦
+---------------------------------------------------------+
                  ¦ Repository calls
+-----------------?---------------------------------------+
¦  Repository Layer                                        ¦
¦  TaskRepository (object singleton)                       ¦
¦   +- reads/writes LocalTaskDatabase (SQLite)             ¦
¦   +- syncs from/to Supabase via SupabaseClient           ¦
¦   +- triggers AlarmScheduler, TaskLocationFacade         ¦
+----------------------------------------------------------+
         ¦                 ¦
+--------?------+   +------?------------------------------+
¦ Local Storage ¦   ¦ Remote Storage                       ¦
¦ SQLiteOpenHelper¦ ¦ Supabase REST API (PostgreSQL)       ¦
¦ LocalTaskDatabase¦ ¦ SupabaseClient (OkHttp)             ¦
+---------------+   +--------------------------------------+
```

## Feature Box Architecture

Each major feature is organized as an isolated "feature box" with a public Facade:

```
feature/
+-- ai/              — AI assistant, multi-LLM engine, voice, memory, tool executor
¦   +-- AiFeatureFacade.kt  (public API)
+-- auth/            — Session management, login screen
¦   +-- AuthFeatureFacade.kt (public API — sessionState, login/logout)
+-- settings/        — App settings screen
¦   +-- SettingsFacade.kt (stub)
+-- sync/            — Cloud sync, offline queue management
¦   +-- TaskSyncFacade.kt (public API — triggers sync)
+-- tasks/
    +-- alarm/       — Alarm scheduling, ringing service, alarm activity, tone picker
    ¦   +-- TaskAlarmFacade.kt (public API)
    +-- location/    — Geofencing, location search, AI location notifier
        +-- TaskLocationFacade.kt (public API)
```

**Facade Pattern:** External code calls `XxxFeatureFacade`, never internal objects directly. Example:
```kotlin
// MainActivity calls:
AuthFeatureFacade.sessionState.collectAsState()
TaskLocationFacade.syncGeofencesWithActiveTasks(this)
```

## Data Flow — Task Lifecycle

```
User creates task in CreateTaskDialog
    ? TaskViewModel.createTask()
        ? TaskRepository.insertTask()
            ? LocalTaskDatabase.insertTask()   [immediate local write]
            ? SyncManager.enqueueSyncAction()  [queue for remote sync]
            ? AlarmScheduler.scheduleAlarm()   [if due time set]
            ? TaskLocationFacade.addGeofence() [if lat/lng set]
            ? async: SupabaseClient.insertTask() [remote sync]
```

## Sync Architecture

**Offline-First with Sync Queue:**
1. All writes go to SQLite immediately (no wait for network)
2. Write is enqueued in `sync_queue` table (`INSERT/UPDATE/DELETE`)
3. `SyncManager` observes network connectivity via `ConnectivityManager`
4. On reconnect: flushes `sync_queue` to Supabase in FIFO order (Mutex-protected)
5. On success: task `sync_status` updated to `SYNCED`
6. `onSyncComplete` callback refreshes `TaskRepository` from local DB

## AI Architecture — Multi-Tier Fallback Ladder

```
User message
    ? AiEngine.chat()
        ? Tier 1: Groq llama-3.3-70b-versatile (primary)
        ? Tier 2: Gemini 2.5-flash (multimodal fallback)
        ? Tier 3: Groq llama-3.1-8b-instant (lightweight fallback)
        ? Tier 4: Local deterministic rule engine (offline fallback)
    ? AiToolExecutor.execute() — parses tool calls from LLM response
        ? create_task / update_task / toggle_task_completion / delete_task / etc.
        ? Mutates TaskRepository directly
    ? AiMemoryManager — injects "learned user rules" into system prompt
```

## Authentication Flow

```
App start ? MainActivity
    ? AuthFeatureFacade.sessionState.collectAsState()
    ? null ? AuthFeatureFacade.LoginView() (LoginScreen)
    ? non-null ? MainScreen() (with bottom nav)

Login: user enters 4-char access code + display name
    ? SupabaseClient.validateAccessCode(code)
    ? AuthManager.saveSession(code, name)
    ? sessionState emits UserSession ? app navigates to MainScreen
```

## Navigation

- **NavController** with `NavHost` — single-activity navigation
- **5 bottom nav tabs:** Tasks, Lists, Habits, Calendar, Analytics
- **AI Assistant** — full-screen route (`ai_assistant`), hides bottom nav
- Routes defined in sealed class `Screen` inside `BottomNavBar.kt`

## Wake Word Integration

- `AiVoiceManager.startWakeWordDetection(context)` called from `MainScreen` `DisposableEffect`
- Runs whenever current route ? `ai_assistant`
- On "Hey Buddy" detected ? navigate to `ai_assistant` route + activate voice mode

## Key Entry Points

| File | Role |
|---|---|
| `KairoApplication.kt` | App singleton — initializes SyncManager |
| `MainActivity.kt` | Single Activity — requests permissions, sets Compose content, syncs geofences |
| `MainScreen.kt` | Root Composable — NavHost, bottom bar, wake word lifecycle |
| `TaskRepository.kt` | Data source of truth — loads from SQLite, syncs to Supabase |
| `AiEngine.kt` | Multi-LLM dispatcher — routes chat requests through fallback ladder |
