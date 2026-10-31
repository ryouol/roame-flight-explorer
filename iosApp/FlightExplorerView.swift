import SwiftUI
import MapKit
import Shared

struct FlightExplorerView: View {
    @ObservedObject var model: FlightModel
    @State private var savedTab = false
    @State private var mapMode = false
    @State private var filtersOpen = false
    @State private var destination: String?
    @State private var detailOpen = false
    @State private var savedDetailID: String?
    @State private var mapPosition: MapCameraPosition = .region(MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 30, longitude: 0),
        span: MKCoordinateSpan(latitudeDelta: 120, longitudeDelta: 300)))
    @State private var focusedFlightID: String?

    private var state: FlightScreenState { model.state }

    var body: some View {
        TabView(selection: $savedTab) {
            NavigationStack {
                VStack(spacing: 8) {
                    statusBanner
                    searchControls
                    discoveryContent
                }
                .navigationTitle("Flight Explorer")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { refreshButton }
                .sheet(isPresented: $filtersOpen) { FilterSheet(model: model) }
                .sheet(isPresented: Binding(get: { destination != nil }, set: { if !$0 { destination = nil } })) {
                    if let group = state.destinations.first(where: { $0.airport == destination }) {
                        NavigationStack {
                            flightList(group.flights) { flight in
                                select(flight)
                                destination = nil
                            }
                            .navigationTitle("\(group.city) · \(group.airport)")
                            .navigationBarTitleDisplayMode(.inline)
                            .toolbar { Button("Done") { destination = nil } }
                        }
                        .presentationDetents([.medium, .large])
                    }
                }
                .sheet(isPresented: Binding(get: { detailOpen && state.selectedFlight != nil && !mapMode }, set: { detailOpen = $0 })) {
                    if let flight = state.selectedFlight {
                        FlightDetailSheet(flight: flight, model: model, showOnMap: {
                            detailOpen = false
                            mapMode = true
                        })
                    }
                }
            }
            .tabItem { Label("Discover", systemImage: "airplane") }
            .tag(false)

            NavigationStack {
                VStack(spacing: 0) {
                    statusBanner
                    if state.saved.isEmpty {
                        ContentUnavailableView("Your next trip starts here", systemImage: "bookmark", description: Text("Save flights from Discover to find them here, even offline."))
                    } else {
                        flightList(state.saved) { savedDetailID = $0.id }
                    }
                }
                .navigationTitle("Saved flights")
                .toolbar { refreshButton }
                .sheet(isPresented: Binding(get: { savedDetailID != nil && state.saved.contains { $0.id == savedDetailID } }, set: { if !$0 { savedDetailID = nil } })) {
                    if let flight = state.saved.first(where: { $0.id == savedDetailID }) {
                        FlightDetailSheet(flight: flight, model: model, showOnMap: nil)
                    }
                }
            }
            .tabItem { Label("Saved (\(state.saved.count))", systemImage: "bookmark.fill") }
            .tag(true)
        }
        .alert("Change not saved", isPresented: Binding(get: { state.actionError != nil }, set: { if !$0 { model.store.dismissActionError() } })) {
            Button("OK") { model.store.dismissActionError() }
        } message: { Text(state.actionError ?? "") }
    }

    private var refreshButton: some View {
        Button { model.store.refresh() } label: { Label("Refresh", systemImage: "arrow.clockwise") }
            .disabled(state.refreshing || state.loading)
    }

    @ViewBuilder private var statusBanner: some View {
        if state.refreshing { ProgressView().frame(maxWidth: .infinity).padding(4).accessibilityLabel("Refreshing flights") }
        if let error = state.refreshError {
            HStack {
                Image(systemName: "wifi.exclamationmark")
                Text(error).font(.caption)
                Spacer(minLength: 4)
                Button("Retry") { model.store.refresh() }.disabled(state.refreshing)
            }
            .padding(12)
            .background(Color.teal.opacity(0.1))
        }
    }

    private var searchControls: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                searchField("From", value: Binding(get: { state.filters.origin }, set: { model.store.setOrigin(value: $0) }))
                searchField("To", value: Binding(get: { state.filters.destination }, set: { model.store.setDestination(value: $0) }))
            }
            HStack {
                Text("\(state.flights.count) flights").font(.subheadline.weight(.semibold))
                Spacer()
                Button { filtersOpen = true } label: { Label("Filters", systemImage: "slider.horizontal.3") }
                Button("Reset") { model.store.resetFilters() }
            }
            if !state.filters.activeLabels.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(Array(state.filters.activeLabels.enumerated()), id: \.offset) { _, label in
                            Button(label) { filtersOpen = true }
                                .font(.caption).padding(.horizontal, 10).padding(.vertical, 6)
                                .background(Color.teal.opacity(0.1), in: Capsule())
                        }
                    }
                }
            }
            Picker("View", selection: $mapMode) {
                Text("List").tag(false)
                Text("Map").tag(true)
            }
            .pickerStyle(.segmented)
        }
        .padding(.horizontal)
        .padding(.top, 8)
    }

    private func searchField(_ title: String, value: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            TextField("Airport or city", text: value)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .accessibilityLabel("\(title) airport or city")
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.2)))
    }

    @ViewBuilder private var discoveryContent: some View {
        if state.loading || (!state.hasCache && state.refreshing) {
            Spacer()
            ProgressView("Finding flights…")
            Spacer()
        } else if !state.hasCache {
            ContentUnavailableView("Flights are unavailable", systemImage: "wifi.slash", description: Text("Start the local API and use Retry or Refresh."))
        } else if state.flights.isEmpty {
            ContentUnavailableView(state.cacheCount == 0 ? "No flights available" : "No matching flights", systemImage: "magnifyingglass",
                description: Text(state.cacheCount == 0 ? "The API returned an empty list. Try refreshing later." : "Try a different route or reset your filters."))
        } else if mapMode {
            FlightMapView(groups: state.destinations, selected: state.selectedFlight, position: $mapPosition,
                focusSelection: focusedFlightID != state.selectedFlight?.id,
                onFocused: { focusedFlightID = $0 }, onDestination: { destination = $0 })
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    if let flight = state.selectedFlight {
                        VStack(spacing: 0) {
                            FlightCard(flight: flight, model: model, onSelect: nil)
                            Button("Close selection") { model.store.selectFlight(id: nil) }.padding(.bottom, 8)
                        }
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                        .padding(12)
                    }
                }
        } else {
            flightList(state.flights) { select($0); detailOpen = true }
        }
    }

    private func select(_ flight: Flight) {
        model.store.selectFlight(id: flight.id)
        focusedFlightID = nil
    }

    private func flightList(_ flights: [Flight], onSelect: @escaping (Flight) -> Void) -> some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(flights, id: \.id) { flight in
                    FlightCard(flight: flight, model: model, onSelect: { onSelect(flight) })
                        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                }
            }.padding()
        }
        .background(Color(.systemGroupedBackground))
    }
}

struct FlightCard: View {
    let flight: Flight
    @ObservedObject var model: FlightModel
    var onSelect: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ViewThatFits(in: .horizontal) {
                HStack { route; Spacer(); price }
                VStack(alignment: .leading) { route; price }
            }
            Text("\(flight.originCity) to \(flight.destinationCity)").font(.subheadline)
            Text("\(flight.departureDate) · \(flight.cabin)").font(.subheadline).foregroundStyle(.secondary)
            HStack {
                Text(flight.program).font(.caption.weight(.semibold))
                Spacer()
                if let onSelect { Button("Details", action: onSelect) }
                Button { model.toggleSave(flight) } label: {
                    if model.state.isPending(id: flight.id) { ProgressView() }
                    else { Text(model.state.isSaved(id: flight.id) ? "Remove" : "Save") }
                }
                .buttonStyle(.borderedProminent)
                .disabled(model.state.isPending(id: flight.id))
                .accessibilityLabel("\(model.state.isSaved(id: flight.id) ? "Remove saved" : "Save") flight \(flight.origin) to \(flight.destination) on \(flight.departureDate)")
            }
        }
        .padding(16)
    }

    private var route: some View { Text("\(flight.origin) → \(flight.destination)").font(.title3.weight(.bold)) }
    private var price: some View { Text("\(Int(flight.points).formatted()) pts").font(.headline).foregroundStyle(.teal) }
}

private struct FlightDetailSheet: View {
    let flight: Flight
    @ObservedObject var model: FlightModel
    let showOnMap: (() -> Void)?
    @Environment(\.dismiss) private var dismiss
    @State private var position: MapCameraPosition = .automatic

    var body: some View {
        NavigationStack {
            ScrollView {
                FlightMapView(groups: [], selected: flight, position: $position).frame(height: 250)
                FlightCard(flight: flight, model: model, onSelect: nil)
                if let showOnMap { Button("Show on discovery map") { dismiss(); showOnMap() }.buttonStyle(.bordered) }
            }
            .navigationTitle("Flight details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("Done") { dismiss() } }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct FilterSheet: View {
    @ObservedObject var model: FlightModel
    @Environment(\.dismiss) private var dismiss
    @State private var start: Date
    @State private var end: Date
    @State private var cabin: String
    @State private var maximum: String
    @State private var programs: Set<String>
    @State private var error: String?

    private static var formatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }

    init(model: FlightModel) {
        self.model = model
        let filters = model.state.filters
        _start = State(initialValue: Self.formatter.date(from: filters.startDate) ?? Self.formatter.date(from: "2027-02-01")!)
        _end = State(initialValue: Self.formatter.date(from: filters.endDate) ?? Self.formatter.date(from: "2027-04-01")!)
        _cabin = State(initialValue: filters.cabin)
        _maximum = State(initialValue: filters.maximumPoints?.stringValue ?? "")
        _programs = State(initialValue: Set(filters.programs))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Departure dates · inclusive") {
                    DatePicker("From", selection: $start, displayedComponents: .date)
                    DatePicker("Through", selection: $end, displayedComponents: .date)
                }
                Section("Cabin and points") {
                    Picker("Cabin", selection: $cabin) {
                        Text("Any cabin").tag("")
                        ForEach(["Economy", "Premium Economy", "Business"], id: \.self) { Text($0).tag($0) }
                    }
                    TextField("Maximum points · no limit", text: $maximum).keyboardType(.numberPad)
                        .accessibilityLabel("Maximum points")
                }
                Section("Loyalty programs · any selected") {
                    ForEach(["Aeroplan", "Avios", "Flying Blue", "Mileage Plan", "MileagePlus"], id: \.self) { program in
                        Toggle(program, isOn: Binding(get: { programs.contains(program) }, set: {
                            if $0 { programs.insert(program) } else { programs.remove(program) }
                        }))
                    }
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .environment(\.timeZone, TimeZone(secondsFromGMT: 0)!)
            .navigationTitle("Filters")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        error = model.store.applyFilters(startDate: Self.formatter.string(from: start), endDate: Self.formatter.string(from: end), cabin: cabin, maximumPoints: maximum, programs: Array(programs))
                        if error == nil { dismiss() }
                    }
                }
            }
        }
    }
}
