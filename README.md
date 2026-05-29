# LRC Studio

**Cross-platform LRC lyrics editor with audio sync** — built with Compose Multiplatform.

Create, edit, and sync `.lrc` lyrics files with your music. Supports Android (API 26+) and Linux desktop via a shared JVM target.

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)
![Desktop](https://img.shields.io/badge/Platform-Linux%20Desktop-FCC624?logo=linux)
![Compose](https://img.shields.io/badge/UI-Compose%20Multiplatform-4285F4?logo=jetpackcompose)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-green)

---

## Features

- **Tap-to-sync workflow** — tap a lyric line, press the Time button (or tap the flash icon) to stamp the current audio position
- **Playback integration** — seek to any line, auto-scroll follows playback, flash-fade animation on the current playback line
- **Fine-tuning** — ±100ms timestamp micro-adjustments per line, batch shift all timestamps by an offset
- **Inline editing** — long-press any line to edit text, insert new lines above/below
- **Preview mode** — hides all editing controls for a clean reading experience
- **Clear timestamps** — remove individual timestamps or clear all at once
- **Import/Export** — open existing `.lrc` files, save with metadata headers (title, artist, album, composer), copy plain LRC to clipboard
- **Audio file support** — load local audio files, metadata (title/artist) is auto-extracted
- **Material Design 3** — dynamic theming with light/dark mode
- **Persistent storage** — songs and settings saved as JSON files

## Screenshots
<img src="https://github.com/GoldenWarriorM/lrc-studio/blob/main/screenshots/Library.webp" alt="Library screen" width="30%" />
<img src="https://github.com/GoldenWarriorM/lrc-studio/blob/main/screenshots/Editor.webp" alt="Editor with synced lyrics" width="30%" />
<img src="https://github.com/GoldenWarriorM/lrc-studio/blob/main/screenshots/Preview.webp" alt="Preview" width="30%" />
<img src="https://github.com/GoldenWarriorM/lrc-studio/blob/main/screenshots/Export.webp" alt="Export screen" width="30%" />

## Getting Started

### Prerequisites

- **JDK 21** (JDK 26+ is incompatible with Kotlin compiler's `JavaVersion.parse()`)
- Android SDK (for Android builds)
- Gradle (wrapped — `./gradlew`)

### Build & Run

```bash
# Desktop (Linux)
sdk use java 21.0.5-tem
./gradlew :composeApp:run

# Android debug APK
./gradlew :composeApp:assembleDebug
```

### Build Android release

```bash
./gradlew :composeApp:assembleRelease
```

APK is at `composeApp/build/outputs/apk/release/`.

## Usage

### Creating a new song

1. Tap **"Add New Song"** on the library screen
2. Paste lyrics (one line per stanza) or import an `.lrc` file
3. Import a music file for audio playback

### Syncing lyrics

1. Tap a lyric line to select it as the **time target** (highlighted with accent color)
2. Press the **Time** button (bottom center) to stamp the current playback position
3. The timestamp is captured and the next line is auto-selected for rapid tap-to-sync

### Editing controls

| Action | Gesture |
|--------|---------|
| Select line / seek audio | Tap the lyric card |
| Clear timestamp | Tap the **X** button |
| Delete line | Long-press the **X** button |
| Edit text | Long-press the lyric card |
| Insert line above/below | Edit buttons appear during inline editing |
| Fine-tune timestamp | Tap **+100ms** or **-100ms** |
| Snap to current position | Tap the **Snap** (TouchApp) icon |

### Preview mode

Toggle the **Preview** button (eye icon) in the toolbar to hide all editing controls. The lyric list becomes clean and read-only — ideal for reviewing finished work.

### Saving

Tap the **Save** icon in the toolbar, fill in metadata (optional), and choose:
- **Save** — writes to disk as an `.lrc` file with metadata headers
- **Copy plain LRC** — copies timestamps + text to clipboard without metadata

## Project Structure

```
composeApp/src/
├── commonMain/kotlin/com/lrcstudio/app/
│   ├── App.kt                     # Root composable, navigation
│   ├── data/
│   │   ├── model/                 # LrcLine, Song data classes
│   │   ├── parser/                # LrcParser (parse/generate)
│   │   └── repository/            # SongRepository, SettingsRepository
│   ├── domain/usecase/            # SyncUseCase
│   ├── navigation/                # Screen sealed class
│   ├── theme/                     # Material3 theme, colors
│   ├── ui/
│   │   ├── components/            # Shared composables
│   │   ├── editor/                # EditorScreen + EditorViewModel
│   │   ├── library/               # LibraryScreen + LibraryViewModel
│   │   ├── picker/                # File picker wrappers
│   │   ├── player/                # AudioPlayer abstraction
│   │   └── settings/              # SettingsScreen
│   └── util/                      # File I/O, metadata extraction
├── androidMain/                   # Android-specific implementations
└── jvmMain/                       # Desktop-specific implementations
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Compose Multiplatform 1.7.1, Material Design 3 |
| Language | Kotlin 2.0.21 |
| Build | Gradle 8.13, KMP |
| Serialization | kotlinx-serialization-json |
| DI | Koin 4.0 |
| Audio (Android) | Media3 ExoPlayer |
| Audio (Desktop) | javax.sound |

## License

LRC Studio is published under the AGPLv3 license.

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.
