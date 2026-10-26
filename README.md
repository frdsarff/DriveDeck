<div align="center">

# 🚗 DriveDeck

### Your drive, simplified.

**A head-unit-inspired dashboard with music, synchronized lyrics, and entertainment shortcuts.**

**Created by [FrdsArff](https://github.com/frdsarff)**

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

</div>

---

## ✨ About DriveDeck

DriveDeck is an early-stage Android dashboard project inspired by modern car head-unit interfaces. It combines a landscape home dashboard, media shortcuts, and a dedicated music player with synchronized lyrics.

The latest UI update takes inspiration from AutoDeck's home/player switching concept and uses LyricDeck as a visual reference for the lyrics player. Existing media detection and lyrics components are retained where possible.

> **Status:** The dashboard/player UI has been committed to the \`rebuild/clean-start\` branch and a debug APK build succeeded. It still needs to be tested on a real head unit; a successful build does not guarantee every feature works on every device.

## 🚀 Current UI

### Home dashboard
- Landscape dashboard with a live clock and date.
- Now Playing card with album artwork and track metadata when available.
- Quick links for YouTube Web and RTMKlik.
- Shortcuts for Spotify and YouTube Music.
- Small purple-inspired switcher button to move between Home and Player.

### Lyrics player
- Album artwork and track/artist information.
- Blurred album-art background.
- Previous, Play/Pause, and Next controls through the media bridge.
- Synchronized lyrics with the active line highlighted and surrounding lines dimmed.
- Home button and floating switcher to return to the dashboard.

### Media and lyrics
- Uses the existing \`MediaBridge\` for active media-session information and playback controls.
- Uses \`LyricsRepository\` to retrieve synchronized lyrics.
- May require Notification Access permission to read playback information from other apps.
- Lyrics availability depends on the track and the lyrics provider response.

### Planned / not yet verified
- Weather and configurable dashboard widgets.
- User-selectable layouts and shortcut customization.
- Additional entertainment cards.
- Validation and polish across different head-unit screen sizes.
- A verified Android Auto-specific experience matching the standalone dashboard UI.

**Important architecture note:** The Compose dashboard in \`MainActivity\` is a regular Android app screen. It is not the same UI as the separate Android Auto media service. The experience depends on whether DriveDeck is opened directly on an Android head unit or displayed through Android Auto from a phone.

## 🧰 Built With

| Technology | Purpose |
|---|---|
| Kotlin | Main programming language |
| Jetpack Compose | Dashboard and player UI |
| Material 3 | UI components |
| AndroidX Media3 / media sessions | Media-related integration |
| Gradle Kotlin DSL | Build configuration |

## 📋 Requirements

- Android Studio and a compatible Android SDK to build from source.
- An Android device or head unit capable of installing and running the app directly for the Compose dashboard.
- Internet access for Gradle dependencies and online lyrics lookup.
- Notification Access permission, when required, to read active media sessions from other apps.
- A compatible Android Auto setup to test the separate media integration.

Current Android configuration (see \`app/build.gradle.kts\` for the source of truth):

| Setting | Value |
|---|---|
| Minimum SDK | 28 |
| Compile SDK | 36 |
| Target SDK | 35 |
| Application ID | \`com.example.drivedeck\` |

## 📦 Download the Latest Build

The latest UI update was committed to the \`rebuild/clean-start\` branch.

- [Open the latest GitHub Actions build](https://github.com/frdsarff/DriveDeck/actions/runs/40405173822)
- In the workflow run, download the **\`DriveDeck-APK\`** artifact.
- Extract the downloaded ZIP to find the debug APK.

This is a development build. Test it on a compatible device and report crashes or layout issues with a screenshot and device details.

## 🛠️ Build from Source

1. Clone the repository:

   \`\`\`bash
   git clone https://github.com/frdsarff/DriveDeck.git
   cd DriveDeck
   \`\`\`

2. Open the project in Android Studio and allow Gradle to sync.

3. Build a debug APK:

   \`\`\`bash
   ./gradlew assembleDebug
   \`\`\`

   On Windows:

   \`\`\`bat
   gradlew.bat assembleDebug
   \`\`\`

The APK is normally generated at \`app/build/outputs/apk/debug/app-debug.apk\`.

## 🚘 Android Auto vs. Head-Unit Dashboard

DriveDeck currently has two distinct concepts:

- **Standalone dashboard:** the Compose home and lyrics-player UI in \`MainActivity\`, intended to run as an Android app on a compatible device/head unit.
- **Android Auto media integration:** a separate media service used to experiment with Android Auto media browsing.

Changing the standalone Compose UI does not automatically change Android Auto's native media screen. These experiences must be developed and tested separately.

## 🗺️ Roadmap

- [x] Add an AutoDeck-inspired home/player switcher concept.
- [x] Add a landscape dashboard and entertainment shortcuts.
- [x] Add the first synchronized-lyrics player UI pass.
- [ ] Test the new UI on physical head units and fix device-specific layout issues.
- [ ] Improve active-line scrolling and lyric loading/error states.
- [ ] Add configurable widgets and shortcut/layout settings.
- [ ] Improve metadata and artwork handling across media apps.
- [ ] Continue improving Android Auto media browsing independently.
- [ ] Add screenshots and demonstrations from real devices.

## 🧪 Testing Checklist

Before treating the latest UI as stable, test:

- Home dashboard opens in landscape.
- Purple switcher toggles between Home and Player.
- YouTube Web and RTMKlik links open correctly.
- Notification/media access allows current track information to appear.
- Artwork, track title, artist, and playback controls work with supported media apps.
- Lyrics load and the active line follows playback.
- Layout remains readable at the head unit's actual resolution and display scaling.
- Android Auto media integration is tested separately from the standalone dashboard.

## 🛡️ Safety Notice

DriveDeck is a driving companion concept, not a reason to interact with a screen while driving. Follow local traffic laws, configure the app while parked, and use video features only when safely parked and permitted by applicable rules. DriveDeck does not bypass Android Auto's driving-safety restrictions.

## 📄 License

No license has been specified yet. Until a license is added, assume that the project is not licensed for redistribution or reuse beyond permissions granted by applicable law.

---

<div align="center">

**Designed for the drive. Built with Kotlin.**

Created with ❤️ by [FrdsArff](https://github.com/frdsarff)

<sub>Early-stage project · Features and documentation may change.</sub>

</div>
