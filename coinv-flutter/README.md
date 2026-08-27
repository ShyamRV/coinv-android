# CoinV Flutter

Flutter rewrite of the CoinV cognitive operating system.

## Run

```powershell
C:\Users\shyam\flutter\bin\flutter.bat run `
  --dart-define=ASI_ONE_API_KEY=your_asi_key `
  --dart-define=GEMINI_API_KEY=your_gemini_key
```

Gemini is optional: a deterministic local embedding is used when its key is
absent. ASI:One is required for conversational replies, intervention
classification, and structured decision analysis. Missing credentials are
shown as visible UI errors and never produce silent empty records.

## Included

- Listening and monitoring modes with STT, TTS, visible foreground service,
  headset media-button handling, and cold-start safety.
- SQLite-backed conversations, memories, semantic recall, About Me values,
  context history, decisions, goals, interventions, promises, and timeline.
- Devil's advocate, bias spotting, silent promise capture, commitment guard,
  intervention quieting, and decision/promise follow-up data.
- Dashboard, Voice, Memory, Decisions, Profile, About Me, and Timeline screens.
- Reactive dark/light themes, privacy controls, export, clear-memory, and reset.

The Android package remains `com.coinv.app`, so installing this build replaces
the Kotlin app rather than creating a second launcher entry.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
