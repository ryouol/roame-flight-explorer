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

I started by breaking the brief into requirements, assumptions, and failure cases, especially what should happen to cached results and saved flights when the API is unavailable. I discussed and challenged the approach with Claude before settling on an implementation plan. My experience setting up Xcode projects at Squint informed the decision to get the starter building early, before adding features. Keeping the interfaces native and putting the data rules in KMP gave each platform its own UI while keeping filtering and offline behavior consistent.

Once the approach was defined, I had Codex implement it in phases: the local API, shared data and persistence, native screens, maps, and tests. Codex contributed substantially to the implementation, tests, development setup, and debugging. Builds, automated checks, and native walkthroughs were used to check the results, followed by a simplification and code-review pass. That process caught stale detail sheets, an iOS fast-typing race, and Android route-fitting issues; the verification section above records the checks performed and their limits.
