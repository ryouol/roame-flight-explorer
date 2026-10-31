# Readability and code review — October 31, 2026

Reviewed the complete starter-to-delivery change (`8d2207d..2c154a8`), then reviewed the cleanup. Applied the explicitly requested simplify, code-review, and Karpathy skills. Three independent simplify passes covered reuse, quality, and efficiency; four code-review passes covered breaking changes, change size, testing, and model context.

## Findings and fixes

These are all actionable findings from the review, with locations in the cleaned source.

1. **P2, fixed — Saved detail could reopen after removing and saving the same flight again.** The stored selection ID outlived the saved snapshot. Both native screens now clear that ID when the snapshot disappears; a failed write preserves it. Android waits for the initial saved-data load before clearing a restored selection. Locations: [Android FlightExplorer.kt:63](../androidApp/src/main/kotlin/travel/roame/takehome/android/FlightExplorer.kt#L63), [iOS FlightExplorerView.swift:100](../iosApp/FlightExplorerView.swift#L100).

2. **P2, fixed — A destination removed by refresh could leave a blank sheet or reopen an old sheet when it returned.** Presentation now requires a matching destination group, and both screens clear an invalid destination ID. Locations: [iOS FlightExplorerView.swift:42](../iosApp/FlightExplorerView.swift#L42), [Android FlightExplorer.kt:70](../androidApp/src/main/kotlin/travel/roame/takehome/android/FlightExplorer.kt#L70).

3. **P3, fixed — Long positional constructors obscured the shared state mapping.** Screen state and destination groups now use named arguments, making adjacent flags, errors, coordinates, and counts explicit. Locations: [FlightStore.kt:67](../shared/src/commonMain/kotlin/travel/roame/takehome/FlightStore.kt#L67), [Models.kt:104](../shared/src/commonMain/kotlin/travel/roame/takehome/Models.kt#L104).

4. **P3, fixed — Dense callbacks and duplicated search-field configuration made the UI hard to read.** Expanded chained statements, branches, and long calls with consistent Kotlin/Swift formatting. The two Android airport search inputs share one small composable. Location: [FlightExplorer.kt:346](../androidApp/src/main/kotlin/travel/roame/takehome/android/FlightExplorer.kt#L346); the same formatting pass covers the shared layer, both native screens, and tests.

5. **P3, fixed — Saved flights were sorted during every screen-state calculation.** The repository now establishes ordering when loading saved snapshots and preserves it on saves/removals. Screen-state calculation consumes that ordered list directly. Location: [FlightRepository.kt:46](../shared/src/commonMain/kotlin/travel/roame/takehome/FlightRepository.kt#L46).

6. **P3, fixed — iOS recreated a fixed date formatter on each access.** The filter sheet now reuses one configured formatter. Location: [FlightExplorerView.swift:356](../iosApp/FlightExplorerView.swift#L356).

7. **P3, fixed — iOS repeated the shared default date literals.** The fallback dates now come from the Kotlin model constants. Location: [FlightExplorerView.swift:370](../iosApp/FlightExplorerView.swift#L370).

8. **P2, fixed — Fast iOS text entry could drop characters while waiting for the shared observer.** A native regression run entered `bcn` but the field retained only `b`. The SwiftUI wrapper now publishes the shared store's current state synchronously after each text edit. Search tests assert the exact origin and destination values before checking results. Location: [RoameStarterApp.swift:40](../iosApp/RoameStarterApp.swift#L40).

## Review conclusions and scope

- **Breaking changes:** no findings. The starter had no flight API or saved-data schema requiring compatibility. The API envelope, endpoint configuration, local server arguments, persistence, and native bridge were inspected.
- **Testing:** no additional findings beyond regression coverage for the sheet fixes. The skill's Codex agent/Rust integration-test conventions do not apply to this Kotlin/Swift app. Shared behavior tests, real SQLite tests, HTTP tests, and native checks cover the relevant boundaries.
- **Model context:** not applicable. The app has no inference requests, conversation history, or injected model-context fragments.
- **Change size:** the complete app is 2,442 changed text lines, larger than the skill's 800-line review guideline. Existing milestones and the following dependency boundaries make it reviewable: API → models/filtering/networking → persistence → repository/state → Android → iOS → native verification. The 737-line shared milestone deserves the three finer shared-layer review passes above. The smallest coherent first stage, the API and endpoint tests, is already a 105-line milestone. Historical dependencies in [shared/build.gradle.kts:5](../shared/build.gradle.kts#L5) and [build.gradle.kts:3](../build.gradle.kts#L3), plus the replacement of starter Greeting callers, mean the original milestones are not all independently buildable; they are resolved in the delivered tree. No history rewrite is warranted. The cleanup separates behavior changes from mechanical formatting.

No speculative caching, extra dependency-injection framework, or broad state architecture was introduced. Recomputing discovery groups for 1,000 flights and sorting within each destination group remain simple and inexpensive; splitting the reactive pipeline would add complexity without demonstrated benefit.

Formatting used [ktfmt's Kotlin style](https://kotlin.github.io/ktfmt/) and Xcode's bundled `swift-format`. A follow-up comparison confirmed the formatting pass changed only formatting/import ordering. The additional iOS text-binding fix above was made explicitly after native testing exposed it.

There is no remote repository or pull request, so no PR label or comment was added. This was a local source review. See [Validation](VALIDATION.md) for checks actually run and their limits; it does not claim exhaustive device or concurrency coverage.
