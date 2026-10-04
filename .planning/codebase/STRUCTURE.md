# STRUCTURE.md — KAIRO Directory Layout & Organization
<!-- last_mapped: 2026-10-04 -->

## Root Layout

```
KAIRO/
+-- app/                         — Android application module
+-- adapters/                    — AI adapter context docs (CLAUDE.md, GEMINI.md, GPT_OSS.md)
+-- .agent/                      — GSD planning/workflow tooling
+-- .agents/                     — GSD skills for agents
+-- gradle/
¦   +-- libs.versions.toml       — Version catalog (centralized dependency versions)
+-- build.gradle.kts             — Root Gradle build (empty, plugin declarations only)
+-- settings.gradle.kts          — Project settings (single module: :app)
+-- gradle.properties            — JVM flags, AndroidX opts
+-- README.md                    — Project overview
+-- GSD-STYLE.md                 — Coding style guide
+-- PROJECT_RULES.md             — Project-specific rules for AI agents
+-- model_capabilities.yaml      — AI model configuration reference
```

## App Module Structure

```
app/src/main/
+-- AndroidManifest.xml          — Permissions + component declarations
+-- java/com/kairo/app/
    +-- KairoApplication.kt      — Application class (SyncManager init)
    +-- MainActivity.kt          — Single activity entry point
    ¦
    +-- alarm/                   — Legacy alarm layer (thin forwarders)
    ¦   +-- AlarmReceiver.kt
    ¦   +-- AlarmRingingService.kt
    ¦   +-- AlarmScheduler.kt
    ¦   +-- BootReceiver.kt
    ¦
    +-- data/
    ¦   +-- auth/
    ¦   ¦   +-- AuthManager.kt   — Thin forwarder to feature.auth.AuthManager
    ¦   +-- local/
    ¦   ¦   +-- LocalTaskDatabase.kt  — SQLiteOpenHelper singleton (kairo_local_tasks.db)
    ¦   +-- model/
    ¦   ¦   +-- Task.kt          — Core domain model (data class + enums)
    ¦   ¦   +-- FilterSort.kt    — FilterCriteria, TaskSort, StatusFilter, DateFilter
    ¦   +-- remote/
    ¦   ¦   +-- SupabaseClient.kt — Compat forwarder ? feature.sync.SupabaseClient
    ¦   ¦   +-- TaskDto.kt        — Compat forwarder DTO
    ¦   ¦   +-- AccessCodeDto.kt  — Compat forwarder DTO
    ¦   +-- repository/
    ¦   ¦   +-- TaskRepository.kt — Object singleton, owns StateFlow<List<Task>>
    ¦   +-- sync/
    ¦       +-- SyncManager.kt   — Thin forwarder ? feature.sync.SyncManager
    ¦
    +-- feature/                 — Feature boxes (each with internal impl + public Facade)
    ¦   +-- ai/
    ¦   ¦   +-- AiFeatureFacade.kt
    ¦   ¦   +-- AiAssistantScreen.kt  — Full-screen AI chat UI (31KB)
    ¦   ¦   +-- AiEngine.kt           — Multi-LLM dispatcher (711 lines)
    ¦   ¦   +-- AiClient.kt           — Gemini short-form client
    ¦   ¦   +-- AiToolExecutor.kt     — Agentic task mutations (443 lines)
    ¦   ¦   +-- AiVoiceManager.kt     — STT/TTS + wake word (394 lines)
    ¦   ¦   +-- AiMemoryManager.kt    — Persistent user rules/preferences
    ¦   ¦   +-- AiChatRepository.kt   — Chat history store
    ¦   ¦
    ¦   +-- auth/
    ¦   ¦   +-- AuthFeatureFacade.kt  — Public API
    ¦   ¦   +-- AuthManager.kt        — internal session (SharedPrefs + StateFlow)
    ¦   ¦   +-- LoginScreen.kt        — Access code entry UI (11KB)
    ¦   ¦
    ¦   +-- settings/
    ¦   ¦   +-- SettingsFacade.kt     — Stub facade
    ¦   ¦   +-- SettingsScreen.kt     — Full settings UI (49KB — largest file!)
    ¦   ¦
    ¦   +-- sync/
    ¦   ¦   +-- TaskSyncFacade.kt     — Public API
    ¦   ¦   +-- SupabaseClient.kt     — Full Supabase REST client (318 lines)
    ¦   ¦   +-- SyncManager.kt        — Offline queue + network monitor (211 lines)
    ¦   ¦   +-- TaskDto.kt            — @Serializable DTO with domain mappers
    ¦   ¦   +-- AccessCodeDto.kt      — @Serializable auth DTO
    ¦   ¦
    ¦   +-- tasks/
    ¦       +-- alarm/
    ¦       ¦   +-- TaskAlarmFacade.kt
    ¦       ¦   +-- AlarmActivity.kt       — Full-screen alarm overlay (47KB)
    ¦       ¦   +-- AlarmReceiver.kt       — BroadcastReceiver for alarm actions (9KB)
    ¦       ¦   +-- AlarmRingingService.kt — ForegroundService for audio (18KB)
    ¦       ¦   +-- AlarmScheduler.kt      — AlarmManager exact alarm scheduling (8KB)
    ¦       ¦   +-- TonePickerDialog.kt    — Custom ringtone picker (48KB — 2nd largest!)
    ¦       +-- location/
    ¦           +-- TaskLocationFacade.kt
    ¦           +-- GeofenceManager.kt     — Google Play Services geofencing (183 lines)
    ¦           +-- GeofenceBroadcastReceiver.kt
    ¦           +-- LocationAiNotifier.kt  — AI-driven location reminders (8KB)
    ¦           +-- LocationPickerDialog.kt — Map/search location picker (25KB)
    ¦           +-- LocationSearchHelper.kt — Place search helper (10KB)
    ¦
    +-- location/                — Legacy location layer (thin forwarders)
    ¦   +-- GeofenceBroadcastReceiver.kt
    ¦
    +-- ui/
        +-- MainScreen.kt        — Root Composable with NavHost + wake word lifecycle
        +-- navigation/
        ¦   +-- BottomNavBar.kt  — Bottom nav component + Screen sealed class
        +-- screens/
        ¦   +-- TasksScreen.kt   — Main task list screen (39KB — core screen)
        ¦   +-- PlaceholderScreens.kt — Stub screens (Lists, Habits, Calendar, Analytics)
        ¦   +-- alarm/
        ¦   ¦   +-- AlarmActivity.kt — UI-layer alarm stub (delegates to feature.tasks.alarm)
        ¦   +-- auth/
        ¦       +-- LoginScreen.kt — UI-layer login stub (delegates to feature.auth)
        +-- components/          — Reusable Compose components
        ¦   +-- TaskCard.kt      — Task row component (21KB)
        ¦   +-- CreateTaskDialog.kt — Full create task modal (43KB)
        ¦   +-- EditTaskDialog.kt   — Full edit task modal (43KB)
        ¦   +-- AdjustParentDialog.kt — Parent task picker (30KB)
        ¦   +-- TaskDetailsBottomSheet.kt — Task detail view (24KB)
        ¦   +-- RepeatPickerDialog.kt — Repeat schedule picker (24KB)
        ¦   +-- FilterBottomSheet.kt — Filter UI (13KB)
        ¦   +-- SortBottomSheet.kt — Sort UI (8KB)
        ¦   +-- FilterSortBar.kt — Filter/sort status bar (7KB)
        ¦   +-- SectionHeader.kt — Collapsible section header (5KB)
        ¦   +-- BreadcrumbBar.kt — Subtask navigation breadcrumb (3KB)
        ¦   +-- DeleteTaskConfirmDialog.kt — Delete confirmation (4KB)
        ¦   +-- UndoDoneBar.kt   — Undo snackbar (4KB)
        ¦   +-- LocationPickerDialog.kt — Stub (delegates to feature)
        ¦   +-- TonePickerDialog.kt — Stub (delegates to feature)
        ¦   +-- SettingsDialog.kt — Stub (delegates to feature)
        +-- theme/
        ¦   +-- Color.kt         — Kinetic Dark Material 3 palette
        ¦   +-- Theme.kt         — KAIROTheme composable (always dark mode)
        ¦   +-- Type.kt          — Typography scale
        +-- utils/
            +-- DateUtils.kt     — Date parsing + formatting utilities
```

## Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Kotlin files | PascalCase | `TaskRepository.kt` |
| Composable functions | PascalCase | `TaskCard()`, `SectionHeader()` |
| Objects/Singletons | PascalCase object | `TaskRepository`, `AiEngine` |
| StateFlow fields | camelCase with underscore prefix for private | `_tasks`, `tasks` |
| Screen routes | snake_case strings | `"tasks"`, `"ai_assistant"` |
| Gradle modules | colon-prefixed | `:app` |

## Key File Sizes (by concern)

| File | Size | Notes |
|---|---|---|
| `SettingsScreen.kt` | 49KB | Largest — monolithic settings UI |
| `TonePickerDialog.kt` | 48KB | Heavy audio/media picker |
| `AlarmActivity.kt` | 47KB | Full-screen alarm overlay with animations |
| `CreateTaskDialog.kt` / `EditTaskDialog.kt` | ~43KB each | Comprehensive task forms |
| `TasksScreen.kt` | 39KB | Core task list — primary user surface |
| `AiAssistantScreen.kt` | 31KB | Full AI chat UI |
| `AdjustParentDialog.kt` | 30KB | Parent task management |
| `LocalTaskDatabase.kt` | 27KB | SQLite database layer |
| `TaskRepository.kt` | 26KB | Data orchestration |
| `TaskViewModel.kt` | 25KB | UI state management |
