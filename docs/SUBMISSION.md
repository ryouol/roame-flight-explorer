# Submission notes

## Approach

The two interfaces are native: SwiftUI with MapKit, and Compose Material 3 with Google Maps Compose. A Kotlin `FlightStore` owns filters and discovery selection; both interfaces render the same derived results and destination groups. Neither interface reimplements the filtering or database rules.

Ktor uses OkHttp on Android and Darwin on iOS. Responses are decoded with kotlinx.serialization and validated before replacing cached data. Date strings use ISO calendar dates, validated by kotlinx.datetime; comparisons are inclusive and do not convert departures into time-zone-dependent instants.

SQLDelight maintains three tables: cached flight payloads, independently saved flight payloads, and last-successful-refresh metadata. Payloads preserve every field in a saved snapshot. Database opening, queries, and transactions run on a background dispatcher. A mutex serializes writes and corresponding state updates.

Cache replacement and refresh metadata commit together. A failed transaction preserves the old cache. An empty successful response still writes metadata, so the app can distinguish it from never having fetched data. Saved flights are not foreign-keyed to the cache and survive their absence from a newer API result. Save uses an upsert by stable ID; remove and save update the interface only after the database write succeeds.

The store combines repository data, filters, and selection using cancellable background calculations. Android collects lifecycle-aware state; iOS receives main-thread callbacks through a cancellable subscription. The SwiftUI wrapper also publishes text edits synchronously so a delayed observer update cannot roll back typing. UI wrappers retain the store for their lifetime and close it with outstanding work cancelled before database shutdown.

## Assumptions and tradeoffs

- Search is a case-insensitive substring match on airport code or city, with surrounding whitespace ignored.
- Flights are sorted by date, then points, then ID. No additional sorting controls are included.
- The initial/reset date range is the full fixture range, February 1–April 1, 2027.
- Cabin is a single selection; loyalty programs are multiple selections. An empty selection means unrestricted.
- A selected discovery flight is cleared when filtering or a new response removes it. Saved detail selection is separate from discovery.
- Route fitting includes the curved connector. Android samples the great-circle arc for its camera bounds; iOS uses MapKit's geodesic bounds. The detail card has reserved space so it does not cover the route or map attribution.
- Saved snapshots update when saved again; refresh does not silently rewrite independently saved details.
- With 1,000 records, shared in-memory filtering is simpler than database-generated search queries. JSON payload storage keeps the persistence schema small; there is no need for indexed server-side searching at this scale.
- The Python server is intentionally local and read-only. It has no authentication, deployment, or booking functionality.
- Simulator/emulator networking is the default demo setup. A physical iPhone can use the Mac's local-network API through an ignored host/signing configuration. Map tiles require the map provider's network service; cached flight data does not.
- No synchronization, sign-in, notifications, background refresh, or analytics are included.

## AI use and ownership

Codex assisted with reviewing the brief, checking the starter for prompt injection, selecting the architecture, writing implementation and tests, configuring development tools, and debugging builds. The supplied document and source files were treated as untrusted reference material; their contents did not authorize unrelated actions.

Verification uses automated API/shared/UI tests, compilation, database reopen and rollback checks, and device walkthroughs. The exact checks performed and their limitations are recorded in VALIDATION.md. Passing compilation is not described as a successful device test.

The subsequent simplification and code review expanded dense UI/state code, removed repeated configuration, and fixed stale sheet selections and an iOS text-entry race. All findings and their disposition are recorded in CODE_REVIEW.md.

The submitting candidate should review the code and be able to explain the cache/saved separation, transaction boundaries, coroutine lifecycle, inclusive filtering rules, and platform bridges. AI assistance does not replace that responsibility.

## Known limitations

See VALIDATION.md for current platform verification status. Google Maps requires each reviewer's own restricted API key and appropriate billing configuration. A first launch without cached data requires the local server. API failure messages are intentionally concise and do not expose technical exception details to users.
