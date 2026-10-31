import MapKit
import Shared
import SwiftUI

struct FlightMapView: View {
    let groups: [DestinationGroup]
    let selected: Flight?
    @Binding var position: MapCameraPosition
    var focusSelection = true
    var onFocused: (String) -> Void = { _ in }
    var onDestination: (String) -> Void = { _ in }

    var body: some View {
        Map(position: $position) {
            ForEach(groups, id: \.airport) { group in
                Annotation(
                    group.airport,
                    coordinate: CLLocationCoordinate2D(
                        latitude: group.latitude, longitude: group.longitude)
                ) {
                    Button {
                        onDestination(group.airport)
                    } label: {
                        VStack(spacing: 2) {
                            Text("\(group.airport) · \(group.count)").font(.caption.bold())
                            Text("\(Int(group.minimumPoints).formatted()) pts").font(.caption2)
                        }
                        .padding(.horizontal, 9).padding(.vertical, 6)
                        .foregroundStyle(.white).background(
                            .teal, in: RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(
                        "\(group.city), \(group.count) flights, from \(group.minimumPoints) points")
                }
                .annotationTitles(.hidden)
            }
            if let selected {
                Marker(selected.origin, coordinate: origin(selected)).tint(.teal)
                if !groups.contains(where: { $0.airport == selected.destination }) {
                    Marker(selected.destination, coordinate: destination(selected)).tint(.teal)
                }
                MapPolyline(
                    MKGeodesicPolyline(
                        coordinates: [origin(selected), destination(selected)], count: 2)
                )
                .stroke(.teal, lineWidth: 3)
            }
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        .onChange(of: selected?.id, initial: true) { _, _ in focus() }
        .onChange(of: focusSelection) { _, _ in focus() }
    }

    private func focus() {
        guard focusSelection, let selected else { return }
        let route = MKGeodesicPolyline(
            coordinates: [origin(selected), destination(selected)], count: 2)
        let rect = route.boundingMapRect
        withAnimation {
            position = .rect(
                rect.insetBy(
                    dx: -max(rect.width * 0.2, 200_000), dy: -max(rect.height * 0.35, 200_000)))
        }
        onFocused(selected.id)
    }

    private func origin(_ flight: Flight) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: flight.originLatitude, longitude: flight.originLongitude)
    }
    private func destination(_ flight: Flight) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(
            latitude: flight.destinationLatitude, longitude: flight.destinationLongitude)
    }
}
