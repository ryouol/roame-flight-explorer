# Walkthrough

The accompanying MP4 uses actual Android emulator and iOS simulator captures. Native UI tests drive the repeatable interactions. Chapter labels are added during editing; no app screens or results are simulated. Idle setup time is trimmed, and the iOS relaunch waiting interval is shortened.

Each platform demonstrates:

1. Discover, the full fixture, a points filter that returns no matches, and reset.
2. YYZ → BCN search with eight matching flights.
3. Flight details, a route map, destination-marker selection, and the destination's flight list.
4. Saving a flight and viewing it in Saved.
5. A separate app process launch after the API has stopped, with a stale-data indication, cached search, saved-flight viewing, and offline removal.

The API is stopped between online and offline captures, not merely hidden. Android's recording runner starts and stops its own API subprocess and force-stops the app between phases. iOS online and offline tests run in separate invocations, terminating the app between them; the local API is stopped before the offline invocation. A clean-install failure/Retry check is also tested separately on both platforms.

## Reproduce

Follow README's build and key setup first. With a disposable Android emulator running and port 8080 free:

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
python3 scripts/android_walkthrough.py --record
```

This produces three raw videos and matching instrumentation logs in `work/acceptance/`. The script clears this demo app's data for the online and cold-start phases.

For iOS, follow README's three separate UI test phases. Capture the simulator using Xcode's Device Hub recording control or `xcrun simctl io YOUR_SIMULATOR_UDID recordVideo --codec=h264 output.mp4`, stopping the recorder with Ctrl-C after each phase.

The native combined-filter tests additionally exercise cabin, multiple loyalty programs, points, and a changed start date. Shared tests cover boundaries, successful empty responses, failed writes, and other cases that are not all shown in the short video. See VALIDATION.md for exact coverage and limits.
