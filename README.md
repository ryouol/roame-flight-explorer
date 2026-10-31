# Flight Explorer

Native SwiftUI and Jetpack Compose flight discovery, backed by shared Kotlin Multiplatform networking, filtering, state, and SQLite persistence.

## What is included

- Discover 1,000 dated flights using origin/destination search, inclusive date and points bounds, cabin, and multiple loyalty programs.
- Switch between a list and destination-grouped maps. Markers show flight count and lowest points price; selecting a flight shows its route.
- Save complete flight snapshots on the device. Cached discovery and saved-flight removal work after relaunch without the API.
- A standard-library Python API, shared tests, Android screen tests, and submission notes in `docs/`.

## Prerequisites

- Python 3.10 or newer for the local API; no pip packages needed.
- Android Studio compatible with AGP 9.3.1 (tested with Rabbit 2), Android SDK Platform **37.0**, Build Tools **37.0.0**, and an ARM64 Google APIs emulator on Apple Silicon.
- A Mac with full Xcode 26 or newer (tested with 27.2) and an installed iOS simulator runtime for iOS. Apple command-line tools alone are insufficient.
- Java for launching Gradle. Android Studio's bundled JDK works. The supplied wrapper selects/downloads JDK 25 for the daemon.

The starter versions are preserved: Kotlin 2.4.21, Gradle 9.7.0, AGP 9.3.1, Compose BOM 2026.09.00, Activity 1.13.0, Swift 6. Added dependencies are pinned in the module build files.

On a Mac without a separately registered JDK, set this in the terminal before the Gradle commands below:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 1. Run the local API

From this repository:

```sh
python3 server/server.py
```

`GET http://localhost:8080/flights` returns the original `fixtures/flights.json` envelope and HTTP 200. The API binds only to the local computer. Stop it with Ctrl-C.

Optional test data and port:

```sh
python3 server/server.py --port 8081 --fixture /absolute/path/to/alternate.json
```

The default fixture path is independent of the shell's working directory. Restart the server to load a changed fixture. The apps use port 8080 unless their injected base URLs are changed.

## 2. Android

1. Open this repository in Android Studio and let Gradle sync.
2. Set your Android SDK location in ignored `local.properties`, for example:

   ```properties
   sdk.dir=/Users/YOUR_USER/Library/Android/sdk
   ```

3. Configure a Maps SDK for Android key as described below.
4. Start a Google APIs emulator, select the `androidApp` run configuration, and run.

The emulator accesses the host API at `http://10.0.2.2:8080`. The app allows cleartext HTTP only for this development host and localhost; other traffic uses HTTPS. Physical-device LAN setup is outside the demo scope.

Command-line build and shared tests:

```sh
./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest
```

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

### Google Maps key

Use a Google Cloud project with billing enabled and **Maps SDK for Android** enabled. Create an Android-restricted API key using:

- Package: `travel.roame.takehome.android`
- SHA-1 fingerprint from `./gradlew :androidApp:signingReport`
- API restriction: Maps SDK for Android only

Create an ignored `secrets.properties` at the repository root:

```properties
MAPS_API_KEY=YOUR_RESTRICTED_ANDROID_MAPS_KEY
```

Alternatively set the `MAPS_API_KEY` environment variable before building. Never commit a real key or signing keystore. Each reviewer needs a key restricted to their own debug signing certificate. Without a key, the app builds and lists/saved flights work, but the map shows a setup message.

[Google's Maps SDK setup guide](https://developers.google.com/maps/documentation/android-sdk/get-api-key)

## 3. iOS

1. Install Xcode and an iOS simulator runtime, and complete Xcode's first-launch setup.
2. Open `iosApp/RoameStarter.xcodeproj`.
3. Select `RoameStarter` and an iPhone simulator, then build and run. The Xcode build phase builds and links `Shared` automatically.
4. Keep the local API running. The iOS simulator uses `http://localhost:8080`.

MapKit uses Apple Maps; no Maps key is required. Local networking is allowed through the app's transport-security configuration.

To build from the command line after Xcode setup:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter \
  -sdk iphonesimulator -configuration Debug -derivedDataPath iosApp/build \
  CODE_SIGNING_ALLOWED=NO ARCHS=arm64 build
```

Run shared tests on an Apple Silicon iOS simulator:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  ./gradlew :shared:iosSimulatorArm64Test
```

## Tests

API tests (no external service needed):

```sh
python3 -m unittest discover -s server -t . -v
```

Shared rules, network decoding/failures, SQLite reopen/rollback, and screen-state tests:

```sh
./gradlew :shared:testAndroidHostTest
```

Android screen tests require a running Google APIs emulator and the local API:

```sh
./gradlew :androidApp:connectedDebugAndroidTest
```

For an actual process-stop/offline restart check (optionally recorded), stop the API first:

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
python3 scripts/android_walkthrough.py --record
```

This script owns a temporary API process, stops it between instrumentation runs, and saves logs/videos under `work/acceptance/`. It verifies online discovery and map loading, offline cached search and saved-flight removal, and cold-start Retry. It leaves the API stopped.

Screen tests clear the app's database on the test emulator. Use a disposable emulator, not an installation with saved flights you want to retain.

iOS native UI tests are in the `FlightExplorerUITests` target. Use a disposable iPhone simulator. Build the tests once with Xcode's **Test** action or:

```sh
xcodebuild -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter \
  -destination 'platform=iOS Simulator,id=YOUR_SIMULATOR_UDID' \
  -derivedDataPath work/DerivedData CODE_SIGNING_ALLOWED=NO ARCHS=arm64 build-for-testing
```

Use the same project, scheme, destination and derived-data arguments with `test-without-building`, `-parallel-testing-enabled NO`, and one of:

- `-only-testing:FlightExplorerUITests/FlightExplorerUITests/testOnline`: fresh app data, API running; leaves one saved flight.
- `-only-testing:FlightExplorerUITests/FlightExplorerUITests/testCombinedFilters`: API running; combines cabin, multiple programs, points, and a changed start date.
- `-only-testing:FlightExplorerUITests/FlightExplorerUITests/testOfflineRelaunch`: API stopped, preserve the previous app data; verifies cached search and removes that saved flight.
- `-only-testing:FlightExplorerUITests/FlightExplorerUITests/testColdStartFailure`: API stopped, uninstall/reinstall the app first to clear this disposable installation's data.

Set `DEVELOPER_DIR` as above when Xcode is not the active command-line installation. Offline tests skip while the API is running; execute the phases separately in the stated order.

Test evidence and verification limits are in `docs/VALIDATION.md`. Architecture, assumptions, and AI use are in `docs/SUBMISSION.md`. The walkthrough sequence is in `docs/WALKTHROUGH.md`.
