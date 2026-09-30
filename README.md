# KAIRO – AI-Powered Task, Habits & Daily Operating Companion

**KAIRO** is a personal productivity suite combining task management, flexible to-do lists, habit tracking, and proactive Agentic AI scheduling.

Built as a native Android application using **Kotlin + Jetpack Compose** with **Material 3**, designed from ground up to sync with a single source of truth (**Supabase**).

---

## 📱 Features (Native Android MVP)

- **Kinetic Dark UI**: Built with custom dark palette extracted directly from the Stitch design tokens.
- **Dynamic Task Manager**:
  - **Overdue Section**: Highlighted warning cards with file attachment badges and urgent indicators.
  - **Today Section**: Scheduled time chips and meeting/location context indicators (Google Meet, Boardroom, etc.).
  - **Upcoming Section**: Future roadmap tasks with sprint and design tags.
  - **Completed Section**: Interactive completion toggles with smooth animated pop and strikethrough styling.
  - **Collapsible Accordions**: Smooth chevron rotation and count badges for all sections.
- **Filter & Sort Engine**:
  - Filter by `All`, `Today`, `Urgent`, `Work`, `Finance`, `Completed`.
  - Sort by `Due Date`, `Priority`, `Alphabetical`.
  - Live in-app search bar.
- **Bottom Navigation**:
  - **Tasks** (Active)
  - **Lists**
  - **Habits**
  - **Calendar**
  - **Analytics**
- **Agentic AI Ready**: Top-bar quick launch into the Kairo AI Assistant.

---

## 🛠 Tech Stack

- **Platform**: Native Android (API 26+)
- **UI Framework**: Jetpack Compose + Material 3
- **Language**: Kotlin 2.0
- **Build System**: Gradle 8.9 (Kotlin DSL `build.gradle.kts` + Version Catalogs `libs.versions.toml`)
- **Architecture**: MVVM with unidirectional data flow (StateFlow & Coroutines)
- **Backend / Source of Truth**: Supabase (PostgreSQL & Realtime Sync)

---

## 🚀 Building & Running

### Prerequisites
- JDK 21
- Android SDK (compileSdk 35)

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`
