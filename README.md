# Flight Explorer

Flight discovery for iOS and Android, built with SwiftUI/MapKit and Jetpack Compose/Google Maps. Both apps use one Kotlin Multiplatform layer for networking, filters, selection, and SQLite persistence. The fixture contains 1,000 flights. You can search, filter, explore destination markers, and save flights for later; cached searches and saved flights work after an offline restart.

Architecture, tradeoffs, verification results, and AI disclosure are in [Submission notes](docs/SUBMISSION.md).

## Setup

Use Python 3.10+, Android Studio with Android SDK 37.0 and Build Tools 37.0.0, and a Mac with full Xcode and an iOS simulator runtime. Tested with Android Studio Rabbit 2 and Xcode 27.2. Complete Xcode’s first-launch setup before building.

The starter toolchain is preserved: Kotlin 2.4.21, Gradle 9.7.0, AGP 9.3.1, Compose BOM 2026.09.00, and Swift 6. Gradle downloads JDK 25 on first use. If Java is not registered on your Mac, use Android Studio’s bundled JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Run every terminal command below from the repository root. First builds need internet access.

## Start the API

```sh
python3 server/server.py
```

This serves `GET http://localhost:8080/flights` with the unchanged fixture envelope. No Python packages are needed. Keep it running while trying either app; Ctrl-C stops it. To test another response, use `--fixture /absolute/path/to/flights.json`; `--port` changes the server port but also requires changing the app’s injected URL.

## Run Android

Open the repository in Android Studio and let Gradle sync. Set `sdk.dir` in an ignored `local.properties` if Studio does not configure it automatically. Start a Google APIs emulator and run `androidApp`. Its API address is `http://10.0.2.2:8080`.

For maps, enable billing and **Maps SDK for Android** in your Google Cloud project. Create a key restricted to package `travel.roame.takehome.android`, your debug certificate’s SHA-1 from `./gradlew :androidApp:signingReport`, and Maps SDK for Android. Follow [Google’s setup instructions](https://developers.google.com/maps/documentation/android-sdk/get-api-key). Put the key in an ignored root file:

```properties
# secrets.properties
MAPS_API_KEY=YOUR_ANDROID_MAPS_KEY
```

Alternatively, set the `MAPS_API_KEY` environment variable. Each reviewer needs their own key restriction. Without a key, lists and saved flights work and the map displays a setup message.

To build an APK:

```sh
./gradlew :androidApp:assembleDebug
```

The output is `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Run iOS

Open `iosApp/RoameStarter.xcodeproj`, select the `RoameStarter` scheme and an iPhone simulator, then Run. Xcode builds the shared Kotlin framework automatically. The simulator uses `http://localhost:8080`; MapKit needs no API key.

For a physical iPhone, connect and unlock it, enable Developer Mode, and sign in to Xcode. Create an ignored `iosApp/Local.xcconfig`:

```xcconfig
FLIGHTS_API_HOST[sdk=iphoneos*] = YOUR_MAC.local
DEVELOPMENT_TEAM = YOUR_TEAM_ID
FLIGHTS_BUNDLE_IDENTIFIER[sdk=iphoneos*] = com.yourname.flightexplorer
```

Find the hostname with `scutil --get LocalHostName` and append `.local`. Start the API with `python3 server/server.py --host 0.0.0.0`, keep the phone and Mac on the same Wi-Fi, and run on the phone from Xcode. Approve the Local Network prompt and your developer profile if prompted; tap Retry if the first fetch failed before approval. Keep the Mac awake for refreshes. Personal-team installations expire and need rebuilding.

To check the simulator build from the terminal:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter \
  -sdk iphonesimulator -configuration Debug -derivedDataPath iosApp/build \
  CODE_SIGNING_ALLOWED=NO ARCHS=arm64 build
```

## Test

These checks cover the API, shared rules/persistence on both platforms, and Android lint:

```sh
python3 -m unittest discover -s server -t . -v
./gradlew :shared:testAndroidHostTest :androidApp:lintDebug
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:iosSimulatorArm64Test
```

Native tests use disposable devices because some reset app data. With a Google APIs emulator and the API running, run `./gradlew :androidApp:connectedDebugAndroidTest`. Offline tests skip while the API is running. For the complete Android online/offline/cold-start sequence, stop the API, build the app and tests with `./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest`, then run `python3 scripts/android_walkthrough.py`. This runner starts and stops its own API and leaves it stopped.

For iOS, select a disposable simulator and run individual methods in the `FlightExplorerUITests` target using Xcode’s Test navigator. Run `testOnline` with the API running, stop the API, then run `testOfflineRelaunch` while preserving app data. For `testColdStartFailure`, keep the API stopped and uninstall the app first. Run the combined-filter and saved-detail regression tests with the API running. Run the destination-removal regression separately against `python3 scripts/selection_test_server.py` in place of the normal server, then stop that test server.

For a manual check on each platform: load all flights, search an origin/destination, apply and reset filters, select a map marker and route, and save a flight. Stop the API, fully close and reopen the app, search cached results, and view/remove the saved flight. A clean installation with the API stopped should show Retry. Map tiles still need a network connection; offline support covers flight data.
