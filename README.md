# DriveDeck — Android Auto starter

An early Kotlin/Jetpack Compose phone dashboard plus a Media3 `MediaLibraryService` so Android Auto can discover a media library.

## Included
- Premium dark phone dashboard UI
- Waze, Google Maps and Spotify web/app shortcuts
- Android Auto media service with a demo audio stream
- Android Auto media capability declaration

## Not included yet
- Live weather integration (needs a weather provider/API key)
- Local music library browsing
- User settings, artwork, error/retry handling and automated tests
- Google Play car-app review/approval

## Build
1. Install Android Studio with Android SDK 36 and JDK 17.
2. Open this folder in Android Studio.
3. Allow Gradle sync to download dependencies.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).

This source package has not been built or tested in an Android SDK environment here. If Gradle sync reports a version unavailable in your setup, update plugin/dependency versions to the current stable versions listed by Android Developers.

## Test Android Auto
- Use Android Auto Developer settings and the Desktop Head Unit (DHU).
- Install the debug APK on a compatible Android phone.
- Connect to Android Auto and check whether DriveDeck appears as a media app.
- Test playback, disconnect/reconnect, voice actions, and different display sizes.

## Important platform boundary
The phone Compose dashboard is NOT displayed as a custom dashboard inside Android Auto. Android Auto renders the media browser/player UI from `MediaLibraryService`. To show weather inside Android Auto, implement a separate weather-category app using the Android for Cars App Library templates and meet the current Google Play car-app requirements.

## Before public release
Replace the demo stream with content you have rights to distribute. Add privacy disclosures for any analytics/location/weather features, implement robust playback error handling, and test against the latest Android for Cars quality checklist.
