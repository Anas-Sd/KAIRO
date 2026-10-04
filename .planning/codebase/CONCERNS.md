# CONCERNS.md — KAIRO Technical Debt & Concerns
<!-- last_mapped: 2026-10-04 -->

## ?? HIGH — Security / Critical

### 1. API Keys Hardcoded in Source (CRITICAL)
**Files:**
- `feature/sync/SupabaseClient.kt` L14-15 — Supabase URL + anon JWT key in plaintext
- `feature/ai/AiEngine.kt` L36 — Groq key assembled via string join (`"gsk" + "_" + "tdAdr..."`)
- `feature/ai/AiEngine.kt` L38 — Gemini key Base64-encoded (trivially reversible)
- `feature/ai/AiClient.kt` L22 — Gemini key Base64-encoded (same value)

**Risk:** Any user who decompiles the APK can extract API keys. Supabase anon key leaks the database endpoint. Groq/Gemini keys can be abused for LLM API costs.

**Mitigation needed:** Move keys to `local.properties` ? `BuildConfig`, inject via Gradle. For prod: server-side proxy.

---

### 2. No ProGuard / R8 Code Obfuscation
**File:** `app/build.gradle.kts` L23-24
```kotlin
isMinifyEnabled = false
```
**Risk:** APK is unobfuscated — class names, constants, and logic are fully readable after decompile. Makes the hardcoded keys trivially extractable.

**Mitigation:** Enable `isMinifyEnabled = true` + configure ProGuard rules for Compose + Kotlin serialization.

---

### 3. Release Build Uses Debug Signing
**File:** `app/build.gradle.kts` L25
```kotlin
signingConfig = signingConfigs.getByName("debug")
```
**Risk:** APK released to users/testers is debug-signed (not production-safe, not Play Store compatible without a release keystore).

---

## ?? MEDIUM — Architecture / Quality

### 4. Duplicated Feature Code (Legacy + Active Layers)
Two parallel implementations exist for alarm, location, and sync:
- `alarm/` (legacy) + `feature/tasks/alarm/` (active)
- `location/` (thin stub) + `feature/tasks/location/` (active)
- `data/remote/SupabaseClient.kt` + `feature/sync/SupabaseClient.kt`
- `data/auth/AuthManager.kt` + `feature/auth/AuthManager.kt`
- `data/sync/SyncManager.kt` + `feature/sync/SyncManager.kt`

The legacy layers are thin forwarders to the feature layers. **AndroidManifest.xml registers BOTH** receivers/services in some cases (e.g. `AlarmReceiver` appears twice).

**Risk:** Confusion about which layer to modify, potential double-registration of BroadcastReceivers.

---

### 5. Zero Automated Tests
**No test files in `src/test/` or `src/androidTest/`.** Test dependencies are declared but unused.

**Risk:** Any refactoring has no safety net. Date parsing, alarm scheduling, and sync queue logic are complex but untested.

---

### 6. Giant Files — Monolithic Composables
Several files exceed 40KB with thousands of lines:
- `SettingsScreen.kt` — 49KB (monolithic settings)
- `TonePickerDialog.kt` — 48KB (inline audio + media logic + UI)
- `AlarmActivity.kt` — 47KB (full alarm UI + logic in one file)
- `CreateTaskDialog.kt` / `EditTaskDialog.kt` — ~43KB each (near-duplicate forms)

**Risk:** Hard to navigate, review, and extend. `Create` and `Edit` dialogs appear near-identical and should be unified.

---

### 7. Placeholder Screens (4 of 6 nav tabs are stubs)
`PlaceholderScreens.kt` provides empty composables for:
- `ListsScreen()` — stub
- `HabitsScreen()` — stub
- `CalendarScreen()` — stub
- `AnalyticsScreen()` — stub

**Risk:** 4/5 bottom nav tabs (80%) lead to empty screens. The app is functionally a single-screen app.

---

### 8. `TaskRepository` as `object` with Constructor Illusion
```kotlin
object TaskRepository {
    operator fun invoke(): TaskRepository = this
}
class TaskViewModel(
    private val repository: TaskRepository = TaskRepository()
) : ViewModel()
```
`TaskRepository()` calls `invoke()` which returns the singleton. This looks like DI but isn't. It will confuse future developers and makes true unit testing of `TaskViewModel` without a real DB impossible without restructuring.

---

### 9. No Pagination / Large Dataset Support
`TaskRepository` holds all tasks in a single `MutableStateFlow<List<Task>>`. SQLite query returns all tasks for the user in one call. No pagination, no cursor-based loading.

**Risk:** Performance degrades with hundreds of tasks. Memory pressure on low-end devices.

---

### 10. No Database Migration Strategy
`LocalTaskDatabase` is on version 2. `onUpgrade()` should handle v1 ? v2. If the migration is incorrect (drop-and-recreate), users upgrading could lose local data.

**File:** `data/local/LocalTaskDatabase.kt` (check `onUpgrade()` implementation carefully before any schema change)

---

## ?? LOW — Minor Issues

### 11. `AiScreen` Delegates to `feature/ai/AiAssistantScreen`
`ui/screens/AiAssistantScreen` (referenced in `MainScreen.kt`) is a re-export of `feature/ai/AiAssistantScreen.kt`. The import in `MainScreen.kt` says `import com.kairo.app.ui.screens.AiAssistantScreen` — this is likely a direct type alias or the feature screen is used directly via the `ui.screens` package alias.

**Note:** Verify that `ui/screens/PlaceholderScreens.kt` contains the `AiAssistantScreen` typeref or it's a direct import.

---

### 12. Hardcoded English Strings
No `strings.xml` usage for user-facing text. All strings are hardcoded in Kotlin:
```kotlin
val KairoBackground = Color(0xFF12131A)
// "Today", "Tomorrow", "Overdue" — all inline
```
**Risk:** No localization support. Date labels ("Today", "Tomorrow") are English-only and affect date parsing logic.

---

### 13. Dual `AlarmActivity` Registrations
`AndroidManifest.xml` registers:
- `.feature.tasks.alarm.AlarmActivity`
- `.ui.screens.alarm.AlarmActivity`

The `ui.screens.alarm.AlarmActivity` (481 bytes) is a near-empty stub. Both are exported. The manifest comment says the second was migrated to feature but both remain registered.

---

### 14. Gemini Key Duplication
The same Gemini API key is Base64-encoded in two separate files:
- `feature/ai/AiEngine.kt` — `DEFAULT_GEMINI_ENCODED`
- `feature/ai/AiClient.kt` — `DEFAULT_ENCODED_KEY`

They appear to be the same key stored redundantly.

---

### 15. No Dependency Injection Framework
Manual `object` singletons throughout. Makes unit testing impossible without real Android context. No scoping, no lifecycle awareness beyond what CoroutineScope + StateFlow provide.

## Summary

| Severity | Count | Theme |
|---|---|---|
| ?? Critical | 3 | Security (keys, obfuscation, signing) |
| ?? Medium | 7 | Architecture, testing, UX gaps |
| ?? Low | 5 | Localization, cleanup, duplication |
