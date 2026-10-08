# Prithvi's Notebook

Kotlin + Jetpack Compose + Room notes app, built for the Xiaomi Pad 6.

Features: two-pane tablet layout, autosave, search, pin, 6 note colours,
checklists, freehand sketching (finger or Xiaomi Focus Pen, with Pen-only
mode, eraser, undo), folders and tags with filter chips, delete with undo,
dark mode and Material You colours.

## Get the APK (no Android Studio needed)
1. Create a free GitHub account and a new empty repository.
2. Upload everything in this folder to it (including the hidden .github folder).
3. Open the Actions tab > "Build APK" > Run workflow. After a few minutes,
   download the "PrithvisNotebook-apk" artifact (a zip containing app-debug.apk).
4. Copy app-debug.apk to the Pad, open it, and allow "install unknown apps" if asked.

## Or build in Android Studio
Open this folder, let Gradle sync, then Build > Build APK(s), or press Run with
the Pad connected over USB (Developer options > USB debugging).
