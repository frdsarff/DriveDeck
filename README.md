# DriveDeck 2.0

Clean rebuild of the DriveDeck Android project.

## Build

Every push to `main` runs GitHub Actions. The workflow builds a debug APK and uploads it as the artifact `DriveDeck-2.0-APK`. You can also start a run manually from the Actions tab.

## Current scope

- Android app scaffold, version 2.0.0.
- AndroidX Car App Library service using a supported `ListTemplate`.
- Local HTML preview inside the phone app.
- GitHub Actions APK build pipeline.

## Important

The car screen currently uses supported Android Auto templates. The local HTML page is a phone-side preview, not unrestricted custom UI on the car display. The permissive host validator is for development only and must be restricted before a production release.
