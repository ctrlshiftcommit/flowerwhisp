# FlowerWhisp for Android

Native Android companion for FlowerWhisp, built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, Accessibility, and overlay APIs.

## Build

Use Android Studio's bundled JDK 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` and is ignored by Git.

## Privacy boundary

FlowerWhisp observes only the focused editable node required to place dictated text. It rejects password and numeric-only fields. Audio is retained only as a recovery file until processing succeeds or the user discards it. Cloud mode sends the recording and optional refinement text directly to Groq using a user-provided API key protected by Android Keystore; mock mode makes no provider request.

No API key is embedded in the APK.
