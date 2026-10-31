# Validation record

Verified October 30–31, 2026 on an Apple Silicon Mac. These are checks actually performed, not a claim of exhaustive device coverage.

## Builds and automated tests

| Check | Result |
| --- | --- |
| Gradle 9.7.0 wrapper JAR and distribution | Matched Gradle's published checksums |
| Python API tests | 3 passed: exact fixture envelope/content type, unknown-route 404, alternate empty fixture |
| Shared Android host tests | 14 passed |
| Shared iOS simulator tests | The same 14 passed on iOS 27.2 ARM64 |
| Android debug app and test APKs | Built; Android lint passed |
| iOS app and UI test bundle | Built with Xcode 27.2, Swift 6, ARM64 simulator |

Shared tests cover normalized code/city searches, AND/OR filter composition, inclusive date/points boundaries, reset, filtered marker aggregates, selection retention/exclusion, rapid filter changes, observer cancellation, duplicate-save updates, network/decoding failures, successful empty responses, and failed save/remove operations. Real temporary SQLite databases verify reopen durability, independent saved snapshots, and transaction rollback.

## Native app verification

Android ran on an API 36 ARM64 Google APIs emulator; an API 37.0 emulator also installed and launched the app. iOS ran on an iPhone 17 simulator with iOS 27.2.

- Both apps loaded all 1,000 flights, searched YYZ → BCN (8 results), applied a zero-points filter, displayed no matches, and reset successfully.
- Both rendered their map providers, selected a destination marker, listed its matching flights, selected a flight, displayed the route, saved it, and showed it in Saved.
- Both passed a real process-stop/relaunch with the API stopped: cached searching and viewing/removing a saved flight remained available, with a stale-data message.
- Both passed a separate clean-install/no-API launch showing an actionable failure and Retry.
- Android passed activity recreation with saved data retained.
- Two Android route-bounds tests passed: a northern great-circle arc beyond the airports' latitudes and a route crossing the date line. Camera fitting uses the measured map viewport and includes the curved route.
- An Android test rendered a 360 × 180 dp visible map area above reserved detail-card space and verified that its actual projection contains both airports and the curved route without placing them beneath the card. This guards against a cropping issue found during video review: [Maps Compose's default minimum zoom](https://github.com/googlemaps/android-maps-compose/blob/v8.4.0/maps-compose/src/main/java/com/google/maps/android/compose/MapProperties.kt) prevented fitting long routes. Explicitly allowing a wider zoom range fixes it.
- Both apps' combined native cabin/program/points/date controls produced 42 results for Business + Aeroplan/Avios + ≤50,000 points, then 20 after moving the start date to March 1.
- iOS's online flow passed in dark appearance with extra-extra-extra-large text; Android search/save/recreation and filter/reset checks passed in dark appearance at font scale 1.3. Screenshots were inspected for clipping.
- Google's Android map key was restricted to the app package, local debug certificate, and Maps SDK. Actual map rendering and marker interaction passed. The key is excluded from source control and the submission archive.

The host initially struggled with two concurrent simulators; an Android initial-load timeout disappeared when testing one platform at a time. A Maps crash on older Google Play services was fixed by declaring the documented optional Apache HTTP library. Test-selector issues found during automation were corrected against the controls' actual accessibility trees.

## Limits

No physical-device, release-signing, App Store/Play Store, exhaustive accessibility audit, or long-duration performance testing was performed. Native map gestures use the provider defaults; pan/zoom was not exhaustively automated. Offline flight data is supported; offline map-tile downloads are outside scope. Large-text checks cover the sizes above, not every accessibility setting or device size.

## Evidence locations

- API: `server/test_server.py`
- Shared rules and persistence: `shared/src/commonTest/`, with SQLite factories in `androidHostTest/` and `iosTest/`
- Android UI: `androidApp/src/androidTest/`
- iOS UI: `iosApp/UITests/FlightExplorerUITests.swift`
- Reproducible Android restart/recording runner: `scripts/android_walkthrough.py`

Generated reports and raw device recordings are kept in ignored build/work directories. The edited walkthrough is delivered separately from the source archive. README documents the commands and the required order of offline test phases.
