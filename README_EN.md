<div align="center">

# VoiceNote AI Tutor

**Snap the slide instead of copying it. After class, let the AI walk you through every photo.**

[简体中文](README.md) · [English](README_EN.md)

![platform](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![release](https://img.shields.io/badge/release-v1.5.1-2ea44f)
![size](https://img.shields.io/badge/APK-6.7%20MB-lightgrey)
![license](https://img.shields.io/badge/license-MIT-3da639)

</div>

![Preview](docs/screenshots/overview.png)

## What it does

You record the lecture. When the professor writes something worth keeping, you take a photo and keep listening — no more copying the board.

Later, every photo comes with its own AI explanation. The model knows **when** in the lecture you took it and **what note** you attached. Ask follow-ups and it remembers the thread.

```text
record ──photo markers──▶ after class ──explain each──▶ follow-up / merge / outline
```

Recordings and photos stay on the device. Images are uploaded to your AI provider only when you ask for an explanation.

## Screens

| Playback: per-marker explanations | Dark mode |
|---|---|
| ![playback](docs/screenshots/playback.png) | ![dark](docs/screenshots/dark.png) |

## Features

### Recording

- Foreground service, keeps going on lock screen and in background; AAC 48kHz stereo
- Auto-pauses on incoming calls / audio focus loss and records the cut point
- Warns once after 60s of silence (phone covered by a book, professor walked away)
- Auto-names recordings from your timetable
- Reminds you before class, with a lead time you set yourself (1–60 min)

### Markers

- Short press: capture → crop → note. Long press: capture and save, no interruption
- Multiple photos per marker; review what you shot while still recording
- Re-crop after class — the old explanation is invalidated and the marker returns to pending
- "Needs review" star with a filtered view

### Viewing images

- Pinch to zoom up to 6× in full screen, drag with one finger once zoomed
- Marker dots on the progress bar are colour-coded by AI status and jump on tap or drag

### AI explanations

- Each marker is a full conversation: follow-ups, prompt editing, regenerate, stop mid-stream
- **Merge several markers into one request** sharing a single explanation (a slide often takes 2–3 photos)
- Per-marker custom prompts; whole-lecture digest (outline + glossary + three self-test questions)
- Byte-stable request prefix to hit DeepSeek's context cache
- Offline markdown-it + KaTeX rendering for formulas, tables and code
- Per-marker cost estimate (DeepSeek endpoints only, official peak/off-peak rates)

### Direct chat

- Ask anything or attach photos; the title is auto-generated after the first turn

### Library

- Timeline home with search across titles, notes, AI answers and follow-ups
- Recycle bin, batch selection, zip backup export/import (SHA-256 verified merge)
- In-app update check

## Quick start

### Install

Grab `record-vX.Y.Z.apk` from [Releases](https://github.com/username-1234567/voice-note-ai-tutor/releases).

> Requires Android 10+. You must add an API key in Settings → AI before photos get explanations.

### Build

```bash
git clone https://github.com/username-1234567/voice-note-ai-tutor.git
cd voice-note-ai-tutor
./gradlew assembleRelease
```

Open the root folder in Android Studio as an alternative.

### API key

Defaults to the official DeepSeek endpoint; any OpenAI-compatible `/chat/completions` works. Set base URL, model name and key in Settings → AI.

Cost estimates appear only when the model name contains `deepseek`. For other models, open an [issue](https://github.com/username-1234567/voice-note-ai-tutor/issues) or adapt it yourself.

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · ExoPlayer · CameraX · uCrop · OkHttp SSE · offline markdown-it + KaTeX in a WebView

Schema is at v5, all upgraded through Room Migrations.

## Docs

- [Changelog](CHANGELOG.md)
- [Design notes](docs/)

## Limitations

- Explanations need a cloud model — images are sent to the provider you configure, no offline mode
- UI is Chinese only; open an issue or build your own if you need another language
- Backups embed the API key **unencrypted** — clean it before sharing a zip

## Privacy

- Recordings, photos and AI replies live in the app's private storage. No account, no sync. Uninstalling deletes everything
- Marker photos, prompts and notes are sent to the AI provider you configure. Nothing else leaves the device

## About

Built with AI assistance. The goal was to solve my own problem — I could never keep up with the board — and it turned out useful to classmates with the same issue. Not a commercial product: no support team, updates whenever.

## Feedback

Bugs, ideas, feature requests — open an [issue](https://github.com/username-1234567/voice-note-ai-tutor/issues).

If this saved you from copying the board, a star helps other students find it.

## License

[MIT](LICENSE) © 2026 username-1234567
