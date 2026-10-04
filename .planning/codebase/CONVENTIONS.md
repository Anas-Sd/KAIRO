# CONVENTIONS.md — KAIRO Code Style & Patterns
<!-- last_mapped: 2026-10-04 -->

## Language & Idioms

- **Kotlin 2.0** — idiomatic Kotlin throughout (no Java)
- **Extension functions** — used for helper utilities (e.g. `JSONObject.optNullableString()`)
- **Data classes** — all domain models and UI state (`Task`, `TasksUiState`, `UiToggles`)
- **Sealed classes** — navigation routes (`sealed class Screen`)
- **`when` expressions** — preferred over if-else chains for exhaustive branching
- **`object` singletons** — used for repository, managers, engines (`TaskRepository`, `AiEngine`, `SyncManager`)
- **`by lazy`** — deferred initialization for SharedPreferences, expensive resources
- **`runCatching { }.getOrDefault()` / `.getOrElse()`** — safe enum parsing pattern

## Architecture Conventions

### Singleton Object Pattern
All service-layer components are `object` singletons (no DI framework):
```kotlin
object TaskRepository { ... }
object AiEngine { ... }
object SyncManager { ... }
```

### Facade Pattern
Each feature box exposes a single public object:
```kotlin
// External code only calls:
AuthFeatureFacade.sessionState
TaskLocationFacade.syncGeofencesWithActiveTasks(context)
TaskAlarmFacade.scheduleAlarm(task, context)
```
Internal implementations (`AuthManager`, `GeofenceManager`) are `internal`.

### Compat Forwarder Pattern
`data/remote/` and `data/auth/` and `data/sync/` contain thin compatibility shims that forward to `feature/*`:
```kotlin
// data/remote/SupabaseClient.kt
object SupabaseClient {
    suspend fun getTasks(...) = com.kairo.app.feature.sync.SupabaseClient.getTasks(...)
}
```
This allows legacy import paths to continue working while actual logic lives in feature boxes.

### StateFlow Pattern
```kotlin
// Private mutable / Public read-only pair
private val _tasks = MutableStateFlow<List<Task>>(emptyList())
val tasks: Flow<List<Task>> = _tasks.asStateFlow()
```

### Coroutine Error Handling
- Network calls use `runCatching { }` for functional error handling
- `withContext(Dispatchers.IO)` for all network/disk operations
- `SupervisorJob()` on long-lived coroutine scopes (repo, sync)
- Catch-all `Throwable` in `KairoApplication.onCreate()` guards initialization

### Repository Constructor Oddity
`TaskRepository` is an `object` but `TaskViewModel` declares it as a constructor parameter:
```kotlin
class TaskViewModel(
    private val repository: TaskRepository = TaskRepository()
) : ViewModel()
```
`TaskRepository.invoke()` returns `this`, so `TaskRepository()` is just the singleton. This allows future DI injection without breaking existing code.

## Compose UI Conventions

### Composable Structure
- Screens: `@Composable fun TasksScreen(onNavigateToAi: () -> Unit)` — callbacks for navigation
- Components: stateless where possible, state hoisted to ViewModel
- `@Preview` not widely present (dark mode custom theme makes previews limited)

### State Consumption
```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### Theming
- Always dark mode — `KAIROTheme` uses `darkColorScheme` only (no light variant)
- Named color constants from `Color.kt`: `KairoPrimary`, `KairoBackground`, `KairoSurface`, etc.
- Status bar / nav bar forced to `KairoBackground` in `Theme.kt`

### Navigation
- Routes passed as string constants via `Screen.route`
- `launchSingleTop = true` + `restoreState = true` on all bottom nav navigations

## Data Model Conventions

### Task Domain Model (`data/model/Task.kt`)
```kotlin
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val dueDate: String = "Today",         // human-readable label
    val dueDateMillis: Long? = null,        // machine-readable epoch ms
    val section: TaskSection = TODAY,       // computed bucket
    val position: Int = 0,                 // manual ordering
    val parentId: String? = null,          // subtask hierarchy
    ...
)
```
- Date is stored as both human-readable string AND epoch millis (redundant but practical)
- `section` is a computed field, set by repository on write

### DTO ? Domain Mapping
```kotlin
// TaskDto.kt (feature/sync)
fun toDomain(): Task { ... }
companion object { fun fromDomain(task: Task, userCode: String?): TaskDto { ... } }
```
- `runCatching { Priority.valueOf(...) }.getOrDefault(Priority.LOW)` — safe enum parsing

### Date Utilities (`ui/utils/DateUtils.kt`)
- `parseDueDateMillis(dueDateStr)` — handles: "Today", "Tomorrow", "Yesterday", "Monday"..."Saturday", "day after tomorrow", "MMM d", "yyyy-MM-dd", "dd/MM/yyyy"
- `formatDisplayDate(dueDate, dueDateMillis)` — prefers millis if available

## Error Handling Patterns

| Pattern | Usage |
|---|---|
| `runCatching { }` | Network calls, enum parsing, sync operations |
| `try { } catch (e: Throwable)` | Initialization guards (KairoApplication, MainActivity) |
| `withTimeoutOrNull(5000L) { }` | LLM API calls with hard timeout |
| `Log.e(TAG, ...)` + `.getOrDefault()` | Graceful degradation with logging |
| `.onFailure { }` | Chained error handling on Result types |

## Naming Conventions

| Element | Pattern | Example |
|---|---|---|
| Private backing StateFlow | `_fieldName` | `_tasks`, `_isOnline` |
| Public StateFlow | `fieldName` | `tasks`, `isOnline` |
| BroadcastReceiver actions | `com.kairo.app.ACTION_*` | `ACTION_TRIGGER_ALARM` |
| SharedPrefs names | `kairo_*_prefs` | `kairo_auth_prefs`, `kairo_ai_memory` |
| DB name | `kairo_*_.db` | `kairo_local_tasks.db` |
| Feature facades | `XxxFeatureFacade` | `AuthFeatureFacade`, `TaskSyncFacade` |

## Android-Specific Patterns

- `enableEdgeToEdge()` in MainActivity for immersive display
- `navigationBarsPadding()` on BottomNavBar (handles gesture nav insets)
- `setExactAndAllowWhileIdle()` for alarms (Doze-safe)
- `PendingIntent.FLAG_MUTABLE` on API 31+ for alarm PendingIntents
- Permissions requested at startup in `requestAppPermissions()` (not lazy)
