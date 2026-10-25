# DriveDeck Ultimate Dashboard Patch

This patch replaces `MainActivity.kt` with an expanded phone dashboard containing shortcuts for:
- Waze
- Google Maps
- Spotify
- YouTube Music
- YouTube

## Apply
1. In the repository, open `app/src/main/java/com/example/drivedeck/MainActivity.kt`.
2. Replace the complete file with the `MainActivity.kt` included in this patch.
3. Sync Gradle and build a debug APK in Android Studio.

## Important limits
- This patch adds phone-dashboard shortcuts. It does not turn YouTube or YouTube Music into a native DriveDeck Android Auto media provider.
- The existing `PlaybackService.kt` still exposes the project's demo audio stream.
- YouTube Music playback inside Android Auto should use the official YouTube Music app's supported Android Auto integration. DriveDeck cannot control or mirror its private playback internals just by adding a shortcut.
- YouTube video playback while driving is restricted by Android Auto. This patch does not bypass those restrictions.
- Weather remains a placeholder until a provider/API key is configured.

## Testing
Test the dashboard on a phone, then test DriveDeck's existing media service through Android Auto or the Desktop Head Unit. This patch has not been built in an Android SDK environment, so run Gradle sync/build and resolve any environment-specific errors before installing.
