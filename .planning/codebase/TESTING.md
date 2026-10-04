# TESTING.md — KAIRO Test Structure & Practices
<!-- last_mapped: 2026-10-04 -->

## Current Testing State

**KAIRO has minimal automated tests.** The test infrastructure is configured and available but largely unused.

## Test Infrastructure (Configured)

### Unit Tests
- **Framework:** JUnit 4 (`junit:junit:4.13.2`)
- **Location:** `app/src/test/java/` (standard Android test source set)
- **Runner:** Default JVM test runner

### Instrumentation Tests (Android)
- **Framework:** AndroidX Test + Espresso
  - `androidx.test.ext:junit:1.2.1`
  - `androidx.test.espresso:espresso-core:3.6.1`
  - `androidx.compose.ui:ui-test-junit4` (Compose testing)
- **Location:** `app/src/androidTest/java/`
- **Runner:** `androidx.test.runner.AndroidJUnitRunner` (declared in `defaultConfig`)

### Debug Manifests
- `androidx.compose.ui:ui-test-manifest` — enabled in debug builds

## What Is Tested

Currently: **nothing meaningful is tested.** The project uses manual/exploratory testing during development.

Expected scaffolding exists (gradle deps declared) but no test files were found in the source tree.

## Testing Gaps (Critical)

| Area | Risk | No Test For |
|---|---|---|
| TaskRepository | HIGH | Insert, update, delete, section assignment logic |
| LocalTaskDatabase | HIGH | SQL queries, migrations (v1 ? v2) |
| DateUtils.parseDueDateMillis | HIGH | Date parsing for "Today", "Tomorrow", weekday names |
| AiEngine fallback ladder | HIGH | Tier selection, quota exhaustion, offline fallback |
| AlarmScheduler.calculateTriggerMillis | HIGH | Date arithmetic for alarm scheduling |
| SyncManager offline queue | HIGH | Queue flush order, conflict handling |
| TaskViewModel filter/sort logic | MEDIUM | FilterCriteria application, TaskSort ordering |
| AuthManager session persistence | MEDIUM | SharedPrefs save/load round-trip |

## Recommended Test Strategy (If Adding Tests)

### Unit Tests (JVM — no device needed)

**DateUtils parsing:**
```kotlin
@Test fun parsesTodayCorrectly() {
    val result = DateUtils.parseDueDateMillis("Today")
    assertThat(result).isCloseTo(System.currentTimeMillis(), within(5000L))
}
```

**AlarmScheduler trigger calculation:**
```kotlin
@Test fun calculatesTriggerMillisForTodayAt9am() { ... }
```

**Task domain model defaults:**
```kotlin
@Test fun taskHasCorrectDefaults() {
    val task = Task(title = "Test")
    assertThat(task.priority).isEqualTo(Priority.LOW)
    assertThat(task.section).isEqualTo(TaskSection.TODAY)
}
```

### Instrumented Tests (Device/Emulator)

**LocalTaskDatabase CRUD:**
```kotlin
@RunWith(AndroidJUnit4::class)
class LocalTaskDatabaseTest {
    @Test fun insertAndRetrieveTask() { ... }
    @Test fun syncQueueEnqueueAndDequeue() { ... }
}
```

**Compose UI snapshot tests:**
```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test fun taskCardDisplaysTitle() {
    composeTestRule.setContent { TaskCard(task = sampleTask, ...) }
    composeTestRule.onNodeWithText("Sample Task").assertIsDisplayed()
}
```

## CI/CD

No CI/CD pipeline configured. Build and test run locally:
```bash
./gradlew test               # JVM unit tests
./gradlew connectedAndroidTest  # Instrumented tests (needs device)
./gradlew assembleDebug      # Debug APK build
```

## Mocking

No mocking library configured (no Mockito, MockK, etc.). If tests were added, MockK would be the recommended choice for Kotlin-first mocking:
```kotlin
// Would need: testImplementation("io.mockk:mockk:1.13.x")
val mockDb = mockk<LocalTaskDatabase>()
every { mockDb.insertTask(any()) } returns "task-id"
```
