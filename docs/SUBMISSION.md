# Submission notes

I built separate native interfaces: SwiftUI with MapKit on iOS, and Jetpack Compose with Google Maps on Android. Both use the same Kotlin Multiplatform data and state layer, so filtering, destination grouping, selection, and persistence follow the same rules. The Python API uses only the standard library and serves the supplied fixture unchanged.

Ktor handles requests, kotlinx.serialization decodes responses, and SQLDelight stores cached flights, saved snapshots, and refresh metadata. Cache replacement and metadata commit in one transaction. Saved snapshots are independent of the cache, so a refresh cannot remove them. Saving the same ID updates its snapshot; save/remove success appears only after the write succeeds. Database work and filtering run away from the UI thread, with cancelled obsolete calculations preventing older results from replacing newer searches.

## Assumptions and tradeoffs

Search matches airport codes or city names by case-insensitive substring after trimming whitespace. Date and points limits are inclusive. Filters combine with AND, while selected programs combine with OR. Initial and reset dates cover February 1–April 1, 2027; flights sort by date, points, then ID. Discovery selection clears when excluded, while Saved has its own selection and ignores discovery filters.

With 1,000 records, in-memory filtering and JSON snapshots keep the implementation small and easy to inspect. Dependency construction is manual. Each app keeps its own local database; there is no account, booking, synchronization, or hosted service. Map markers group by destination airport and route previews fit the connector with space reserved for the detail card.

The default demonstration uses an iOS simulator and Android emulator. Android maps require a reviewer-supplied restricted key and billing configuration. Cached flight data works offline, but map tiles depend on the provider’s network service. A first launch without cached data needs the local API. Setup and test commands are in the [README](../README.md).

## Verification

Both native builds passed. Automated checks passed for 15 shared tests on each platform, three API tests, Android lint, eight Android native tests, and three iOS native filter/selection checks. Coverage includes filter boundaries, marker aggregates, SQLite reopen/rollback, independent saved snapshots, network and write failures, rapid filter changes, and observer cancellation.

Both apps were also exercised through online discovery, map selection, saving, a full process restart with the API stopped, cached searching/removal, and a clean launch without the API. Dark appearance and larger text were checked on both. An iPhone 17 Pro Max running iOS 27.2 additionally passed installation, launch, and loading all 1,000 flights; the full acceptance flow was verified on simulators/emulators. These checks do not constitute an exhaustive accessibility audit, long-duration performance test, or release-store validation.

## Approach and AI use

I first broke the task into three parts: shared flight data and rules, the two native interfaces, and behavior after the API goes offline. I treated offline persistence as an architectural requirement: a saved flight must survive both an app restart and its removal from a later API response. That led to separate cached results and saved snapshots. With only 1,000 flights, I chose in-memory filtering and manual dependency construction to keep the shared layer small. My experience setting up Xcode projects at Squint also made me account for the development environment and shared-framework integration as explicit setup work.

Before implementation, I defined acceptance cases in the plan. Searches needed to handle capitalization and surrounding spaces; flights exactly on a date or points boundary needed to remain included; selected programs needed OR logic within the other AND filters. Marker counts and minimum prices had to come from the filtered results. For persistence, the checks included saving twice without duplication, reopening the database, refreshing without a saved flight in the response, and preserving old data after a failed write. Defining those outcomes up front gave me concrete ways to evaluate the implementation.

I discussed and challenged the approach with Claude, then gave Codex the implementation plan in phases: local API, shared data and persistence, native screens, maps, and platform verification. I directed the scope, architecture, and acceptance criteria; Codex helped implement the app and tests, configure tooling, and debug failures. I guided the work through those requirements and requested a separate simplification and code-review pass to keep the code readable and avoid unnecessary abstractions.

Verification included real temporary SQLite databases for reopen and rollback checks, injected network/storage failures, and shared tests on both platforms. Native checks exercised actual search fields, filters, maps, and saved-flight screens. Both apps were terminated and reopened with the API stopped to check that offline behavior survived a new process. These checks exposed stale detail sheets, dropped characters during fast iOS typing, and Android route-fitting issues, which were corrected and checked again. The verification section above gives the results and coverage limits.
