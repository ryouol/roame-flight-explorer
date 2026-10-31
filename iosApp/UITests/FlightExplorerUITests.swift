import XCTest

@MainActor
final class FlightExplorerUITests: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "travel.roame.takehome.ios")

    override func setUp() { continueAfterFailure = false }
    override func tearDown() { app.terminate() }

    // Run separately with scripts/selection_test_server.py replacing the normal API.
    func testRemovedDestinationStaysDismissed() async throws {
        try await requireAPI(running: true)
        app.launch()
        XCTAssertTrue(app.staticTexts["1,000 flights"].waitForExistence(timeout: 30))
        search()
        app.buttons["Map"].tap()
        let marker = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Barcelona, 8 flights")
        ).firstMatch
        XCTAssertTrue(marker.waitForExistence(timeout: 15))
        var request = URLRequest(url: URL(string: "http://localhost:8080/test/empty-next-refresh")!)
        request.httpMethod = "POST"
        let (_, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 204)
        app.buttons["Refresh"].tap()
        marker.tap()
        XCTAssertTrue(app.navigationBars["Barcelona · BCN"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No flights available"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.navigationBars["Barcelona · BCN"].waitForNonExistence(timeout: 5))
        app.buttons["Refresh"].tap()
        XCTAssertTrue(app.staticTexts["8 flights"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.navigationBars["Barcelona · BCN"].exists)
        XCTAssertTrue(app.buttons["Filters"].isHittable)
    }

    // Run with a clean simulator app installation and the API running.
    func testRemovedSavedDetailStaysDismissed() async throws {
        try await requireAPI(running: true)
        app.launch()
        XCTAssertTrue(app.staticTexts["1,000 flights"].waitForExistence(timeout: 30))
        search()
        app.buttons["Save flight YYZ to BCN on 2027-02-01"].tap()
        XCTAssertTrue(app.tabBars.buttons["Saved (1)"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Saved (1)"].tap()
        app.buttons["Details"].tap()
        XCTAssertTrue(app.navigationBars["Flight details"].waitForExistence(timeout: 5))
        let detail = app.scrollViews["flight-detail-content"]
        let remove = detail.buttons["Remove saved flight YYZ to BCN on 2027-02-01"]
        for _ in 0..<3 where !remove.isHittable {
            detail.swipeUp()
        }
        remove.tap()
        XCTAssertTrue(app.staticTexts["Your next trip starts here"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Discover"].tap()
        app.buttons["Save flight YYZ to BCN on 2027-02-01"].tap()
        XCTAssertTrue(app.tabBars.buttons["Saved (1)"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Saved (1)"].tap()
        XCTAssertTrue(app.staticTexts["YYZ → BCN"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.navigationBars["Flight details"].exists)
        XCTAssertTrue(app.tabBars.buttons["Discover"].isHittable)
    }

    func testCombinedFilters() async throws {
        try await requireAPI(running: true)
        app.launch()
        XCTAssertTrue(app.staticTexts["1,000 flights"].waitForExistence(timeout: 30))
        app.buttons["Filters"].tap()
        app.buttons["cabin-filter"].tap()
        app.buttons["Business"].tap()
        for program in ["Aeroplan", "Avios"] {
            let toggle = app.switches[program]
            toggle.switches.firstMatch.tap()
            XCTAssertEqual(toggle.value as? String, "1")
        }
        app.textFields["Maximum points"].tap()
        app.textFields["Maximum points"].typeText("50000")
        app.buttons["Apply"].tap()
        XCTAssertTrue(app.staticTexts["42 flights"].waitForExistence(timeout: 5))
        app.buttons["Filters"].tap()
        app.datePickers["start-date-filter"].coordinate(
            withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)
        ).tap()
        app.buttons["DatePicker.NextMonth"].tap()
        // Use the day cell's frame; this runtime reports an offset activation point.
        app.buttons["Monday, March 1"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .tap()
        if app.buttons["dismiss popup"].exists {
            app.buttons["dismiss popup"].coordinate(
                withNormalizedOffset: CGVector(dx: 0.02, dy: 0.1)
            ).tap()
        }
        XCTAssertEqual(
            app.datePickers["start-date-filter"].buttons.firstMatch.value as? String, "Mar 1, 2027")
        app.buttons["Apply"].tap()
        XCTAssertTrue(app.staticTexts["20 flights"].waitForExistence(timeout: 5))
    }

    func testOnline() async throws {
        try await requireAPI(running: true)
        app.launch()
        XCTAssertTrue(app.staticTexts["1,000 flights"].waitForExistence(timeout: 30))
        app.buttons["Reset"].tap()
        app.buttons["Filters"].tap()
        let points = app.textFields["Maximum points"]
        XCTAssertTrue(points.waitForExistence(timeout: 5))
        points.tap()
        points.typeText("0")
        app.buttons["Apply"].tap()
        XCTAssertTrue(app.staticTexts["No matching flights"].waitForExistence(timeout: 5))
        app.buttons["Reset"].tap()
        search()
        app.buttons["Details"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Flight details"].waitForExistence(timeout: 5))
        let showMap = app.buttons["Show on discovery map"]
        for _ in 0..<3 where !showMap.isHittable { app.scrollViews.firstMatch.swipeUp() }
        showMap.tap()
        XCTAssertTrue(app.buttons["Close selection"].waitForExistence(timeout: 5))
        pause()
        app.buttons["Close selection"].tap()
        let marker = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Barcelona, 8 flights")
        ).firstMatch
        XCTAssertTrue(marker.waitForExistence(timeout: 15))
        marker.tap()
        XCTAssertTrue(app.navigationBars["Barcelona · BCN"].waitForExistence(timeout: 5))
        app.buttons["Details"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Close selection"].waitForExistence(timeout: 5))
        pause()
        app.buttons["Save flight YYZ to BCN on 2027-02-01"].tap()
        XCTAssertTrue(app.tabBars.buttons["Saved (1)"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Saved (1)"].tap()
        XCTAssertTrue(app.staticTexts["YYZ → BCN"].waitForExistence(timeout: 5))
        pause()
    }

    // Run separately after testOnline, with the API stopped and app data retained.
    func testOfflineRelaunch() async throws {
        try await requireAPI(running: false)
        app.launch()
        XCTAssertTrue(app.staticTexts["1,000 flights"].waitForExistence(timeout: 10))
        XCTAssertTrue(
            app.staticTexts[
                "Refresh unavailable. Showing saved device data; it may be out of date."
            ].waitForExistence(timeout: 15))
        search()
        app.tabBars.buttons["Saved (1)"].tap()
        XCTAssertTrue(app.staticTexts["YYZ → BCN"].waitForExistence(timeout: 5))
        pause()
        app.buttons["Remove saved flight YYZ to BCN on 2027-02-01"].tap()
        XCTAssertTrue(app.staticTexts["Your next trip starts here"].waitForExistence(timeout: 5))
        pause()
    }

    // Run separately after uninstall/reinstall on the disposable simulator, API still stopped.
    func testColdStartFailure() async throws {
        try await requireAPI(running: false)
        app.launch()
        XCTAssertTrue(app.staticTexts["Flights are unavailable"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.buttons["Retry"].isHittable)
        pause()
    }

    private func search() {
        let origin = app.textFields["From airport or city"]
        origin.tap()
        origin.typeText("yyz")
        XCTAssertEqual(origin.value as? String, "yyz")
        let destination = app.textFields["To airport or city"]
        destination.tap()
        destination.typeText("bcn\n")
        XCTAssertEqual(destination.value as? String, "bcn")
        XCTAssertTrue(app.staticTexts["8 flights"].waitForExistence(timeout: 10))
        pause()
    }

    private func requireAPI(running: Bool) async throws {
        var request = URLRequest(url: URL(string: "http://localhost:8080/flights")!)
        request.timeoutInterval = 2
        let available = (try? await URLSession.shared.data(for: request)) != nil
        try XCTSkipIf(
            available != running,
            running
                ? "Start the local API for this test."
                : "Stop the API and run this test separately, as documented.")
    }

    private func pause() { Thread.sleep(forTimeInterval: 2) }
}
