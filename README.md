<div align="center">🚗 DriveDeck

Your drive, simplified.

A smarter dashboard for your everyday drive.

Quick access to navigation, music, and an experimental Android Auto media experience — all in one place.

<br>Created by "FrdsArff" (https://github.com/frdsarff)

<br>"Android" (https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
"Kotlin" (https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
"Jetpack Compose" (https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
"Media3" (https://img.shields.io/badge/Media-AndroidX%20Media3-20232A?style=for-the-badge)

</div>---

✨ About DriveDeck

DriveDeck is an Android companion dashboard project designed to make everyday driving apps easier to access.

Built with Kotlin and Jetpack Compose, DriveDeck combines a clean dark interface with shortcuts to navigation and music apps. The project also experiments with Android Auto media browsing using AndroidX Media3.

The vision is simple: less clutter, quick access, and a better driving companion experience.

«🚧 DriveDeck is an early-stage project. Some features are experimental or not yet connected.»

🚀 Features

Feature| Status| Description
- 🗺️ Waze| ✅| Quick access to Waze navigation
- 📍 Google Maps| ✅| Quick access to Google Maps
- 🎵 Spotify| ✅| Shortcut to Spotify
- 🎧 YouTube Music| ✅| Shortcut to YouTube Music
- ▶️ YouTube| ✅| Opens YouTube or its website
- 🚘 Android Auto| 🧪| Experimental Media3 media service
- 🌦️ Live Weather| 🔜| Weather integration planned
- ⚙️ Dashboard Settings| 🔜| Customisation planned

Important: The navigation and music entries above are phone-dashboard shortcuts. They do not automatically become native apps inside Android Auto.

🎨 Design Philosophy

DriveDeck aims to provide a modern, minimal dashboard with:

- 🌑 A clean dark theme
- 🧩 Simple card-based layouts
- ⚡ Quick access to everyday apps
- 📱 A lightweight phone companion interface
- 🚘 An experimental Android Auto media experience

🛠️ Built With

Technology| Purpose
Kotlin| Main programming language
Jetpack Compose| Modern Android UI
Material 3| UI components and styling
AndroidX Media3| Media playback and library service
ExoPlayer| Audio playback engine
Gradle Kotlin DSL| Build configuration

📋 Requirements

- Android Studio
- A compatible Android SDK
- An Android device or emulator
- Internet access for Gradle dependency downloads
- A compatible Android Auto setup for testing the media service

Current Android configuration

Setting| Value
Minimum SDK| 28
Compile SDK| 36
Target SDK| 35
Application ID| "com.example.drivedeck"

These values reflect the current project configuration and may change as development continues.

📦 Getting Started

1. Clone the repository

git clone https://github.com/frdsarff/DriveDeck.git

2. Open in Android Studio

Open the cloned "DriveDeck" project folder and allow Gradle to sync.

3. Build the application

From Android Studio:

1. Select Build.
2. Choose Build Bundle(s) / APK(s).
3. Select Build APK(s).
4. Locate the generated APK from the build output.

Alternatively, on a system with a compatible Gradle environment, run:

./gradlew assembleDebug

On Windows, use:

gradlew.bat assembleDebug

The debug APK is normally generated at:

app/build/outputs/apk/debug/app-debug.apk

🚘 Android Auto Media Integration

DriveDeck uses AndroidX Media3's "MediaLibraryService" to experiment with media browsing and playback in Android Auto.

Current implementation

- Media library session
- ExoPlayer-based playback
- A demo audio stream
- Basic media browsing structure

Current limitations

- The media service currently exposes a demo audio track.
- Native YouTube playback is not implemented.
- Native YouTube Music integration is not implemented.
- Phone-dashboard shortcuts do not grant access to other apps' private playback systems.
- Weather data is not connected to a live provider.

YouTube Music should be accessed through its official supported Android Auto integration. DriveDeck does not bypass Android Auto's driving-safety restrictions.

🗺️ Roadmap

- [ ] Improve Android Auto media browsing
- [ ] Add playback states and richer media metadata
- [ ] Add album artwork support
- [ ] Improve playback error handling
- [ ] Integrate live weather data
- [ ] Add dashboard settings and customisation
- [ ] Improve app launch and fallback handling
- [ ] Add screenshots and demonstrations
- [ ] Test across supported Android devices and Android Auto environments

🧪 Testing

Before considering a feature complete:

- Test the dashboard on a physical Android device.
- Check that app shortcuts open correctly.
- Test missing-app and network-error scenarios.
- Test Android Auto media browsing on compatible hardware or the Desktop Head Unit.
- Verify that changes do not interfere with Android Auto's safety requirements.

🤝 Contributing

Suggestions, bug reports, and improvements are welcome.

When proposing changes, please include:

- A clear description of the improvement
- Steps to reproduce any reported bug
- Relevant screenshots or logs, when available
- Testing performed

🛡️ Safety Notice

DriveDeck is intended to be a driving companion, not a distraction.

- Follow local traffic laws.
- Avoid interacting with your phone while driving.
- Use voice controls where appropriate.
- Use video features only when safely parked and permitted by applicable rules.
- Respect Android Auto's app compatibility and driving-safety restrictions.

📄 License

No license has been specified yet.

Until a license is added, assume that the project is not licensed for redistribution or reuse beyond the permissions granted by applicable law.

---

<div align="center">🚗 DriveDeck

Designed for the drive. Built with Kotlin.

Created with ❤️ by "FrdsArff" (https://github.com/frdsarff)

<sub>Early-stage project · Features and documentation may change.</sub>

</div>
