# CoinV

CoinV is a Flutter cognitive operating system for voice coaching, semantic
memory, decisions, goals, context learning, and timely interventions.

Flutter is the canonical application at the repository root. The retained
Kotlin/Compose implementation lives in `native-android/` as a reference and
installs separately with application ID `com.coinv.app.legacy`.

## Run the Flutter app

```powershell
flutter pub get
flutter run `
  --dart-define=ASI_ONE_API_KEY=your_asi_key `
  --dart-define=GEMINI_API_KEY=your_gemini_key
```

The app defaults to local-only processing. Local mode uses deterministic
on-device embeddings and decision guidance without sending content to an AI
provider. Disable local-only processing in Profile to use configured ASI:One
and Gemini credentials. Keys are compile-time secrets and must never be
committed.

## Features

- Idle, Listening, and Monitoring modes with speech recognition, TTS,
  foreground microphone notification, audio focus, and headset media controls.
- SQLite conversations, memories, semantic recall, value memories, context,
  decisions, task-backed goals, insights, personalization, and timeline.
- Transactional one-time import from the native `coinv_v103.db` database.
- Devil's advocate, bias spotting, promise tracking, commitment guard,
  decision follow-ups, outcome resolution, and intervention quieting.
- Dashboard, Voice, Memory, Decisions, Profile, About Me, and Timeline screens.
- Live dark, light, and system themes; data retention; privacy controls; JSON
  export; memory clearing; and full reset.

## Verify

```powershell
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
flutter test integration_test -d <android-device>
flutter build apk --debug
flutter build apk --release
```

Release signing is loaded from ignored `android/key.properties`:

```properties
storeFile=C:\\secure\\coinv-upload.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file, Gradle can produce an unsigned release artifact for local
verification.

## Retained native Android app

```powershell
cd native-android
.\gradlew.bat assembleDebug
```

The native project reads optional API keys from ignored
`native-android/local.properties`.

## Android platform boundary

Product and business logic is Dart. The small Flutter host in
`android/app/src/main/kotlin/` only exposes Android-required vibration,
accessibility animation scale, speech audio focus, and legacy database path
operations. Foreground services and MediaSession delivery are provided through
Flutter plugins backed by Android services.
