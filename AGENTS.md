# Autonomous Execution & Delivery Rules

## 1. Auto-Push to GitHub
- Immediately push everything to GitHub (`origin/main`) whenever changes are committed after executing any task, fix, or feature.
- Never leave local commits sitting unpushed. Always run `git push origin main` automatically without waiting for explicit user prompting.

## 2. Auto-Build Release APK for Android Changes
- Whenever any changes are made that affect the Android mobile application in `c:\PROJECTS\KAIRO`, automatically build the release APK by running `./gradlew.bat assembleRelease`.
- Ensure the APK build succeeds cleanly and report the release APK status/path without waiting to be asked explicitly.
