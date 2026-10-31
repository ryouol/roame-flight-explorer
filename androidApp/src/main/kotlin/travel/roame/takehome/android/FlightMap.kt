package travel.roame.takehome.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import travel.roame.takehome.DestinationGroup
import travel.roame.takehome.Flight

@Composable
internal fun FlightMap(
    groups: List<DestinationGroup>,
    selected: Flight?,
    camera: CameraPositionState,
    onDestination: (String) -> Unit,
    focusSelection: Boolean = true,
    onFocused: (String) -> Unit = {},
    bottomPadding: Dp = 0.dp,
) {
    if (!BuildConfig.MAPS_CONFIGURED) {
        EmptyState(
            "Map setup required",
            "Add a Maps SDK key to secrets.properties, then rebuild. Flight lists and saved flights are available.",
        )
        return
    }
    var loaded by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val markerPadding = with(LocalDensity.current) { 48.dp.roundToPx() }
    val bottomPixels = with(LocalDensity.current) { bottomPadding.roundToPx() }
    // Keep the native map view stable while reserving camera and attribution space for the card.
    GoogleMap(
        modifier =
            Modifier.fillMaxSize()
                .onSizeChanged { mapSize = it }
                .testTag(if (loaded) "map-loaded" else "map-loading"),
        cameraPositionState = camera,
        onMapLoaded = { loaded = true },
        contentPadding = PaddingValues(bottom = bottomPadding),
        properties = MapProperties(minZoomPreference = 0f),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        groups.forEach { group ->
            key(group.airport) {
                MarkerComposable(
                    group.count,
                    group.minimumPoints,
                    state = rememberUpdatedMarkerState(LatLng(group.latitude, group.longitude)),
                    title = "${group.airport} · ${group.count} flights",
                    snippet = "From ${group.minimumPoints.pointsLabel()} points",
                    onClick = {
                        onDestination(group.airport)
                        true
                    },
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color(0xFF006B60),
                        contentColor = Color.White,
                    ) {
                        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                            Text(
                                "${group.airport} · ${group.count}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                "${group.minimumPoints.pointsLabel()} pts",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        selected?.let { flight ->
            val origin = LatLng(flight.originLatitude, flight.originLongitude)
            val destination = LatLng(flight.destinationLatitude, flight.destinationLongitude)
            Marker(state = rememberUpdatedMarkerState(origin), title = flight.origin)
            if (groups.none { it.airport == flight.destination })
                Marker(state = rememberUpdatedMarkerState(destination), title = flight.destination)
            Polyline(
                points = listOf(origin, destination),
                color = Color(0xFF008577),
                width = 7f,
                geodesic = true,
            )
        }
    }
    LaunchedEffect(selected?.id, loaded, focusSelection, mapSize, bottomPixels) {
        if (loaded && focusSelection && mapSize.height > bottomPixels)
            selected?.let {
                // Let the map's padding update commit before fitting a newly selected route.
                withFrameNanos {}
                val bounds =
                    routeBounds(
                        LatLng(it.originLatitude, it.originLongitude),
                        LatLng(it.destinationLatitude, it.destinationLongitude),
                    )
                val padding =
                    minOf(markerPadding, mapSize.width / 4, (mapSize.height - bottomPixels) / 4)
                camera.move(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                onFocused(it.id)
            }
    }
}
