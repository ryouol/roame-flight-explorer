import SwiftUI
import Shared

@main
struct RoameStarterApp: App {
    @StateObject private var model = FlightModel()

    var body: some Scene {
        WindowGroup {
            FlightExplorerView(model: model)
                .tint(.teal)
        }
    }
}

@MainActor
final class FlightModel: ObservableObject {
    let store: FlightStore
    @Published private(set) var state: FlightScreenState
    private var subscription: FlightSubscription?

    init() {
        let store = PlatformStoreKt.createFlightStore(baseUrl: "http://localhost:8080")
        self.store = store
        self.state = store.currentState
        subscription = store.observe { [weak self] state in
            // The shared subscription delivers on Dispatchers.Main.
            MainActor.assumeIsolated { self?.state = state }
        }
    }

    func toggleSave(_ flight: Flight) {
        if state.isSaved(id: flight.id) { store.removeFlight(id: flight.id) }
        else { store.saveFlight(flight: flight) }
    }

    func setOrigin(_ value: String) {
        store.setOrigin(value: value)
        // SwiftUI reads its binding again before the asynchronous subscription can deliver.
        state = store.currentState
    }

    func setDestination(_ value: String) {
        store.setDestination(value: value)
        state = store.currentState
    }

    isolated deinit {
        subscription?.cancel()
        store.close()
    }
}
