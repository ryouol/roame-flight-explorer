package travel.roame.takehome.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import travel.roame.takehome.Flight

class MapViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fitsBothAirportsOnAPhoneSizedRouteMap() {
        val fitted = AtomicBoolean(false)
        lateinit var camera: CameraPositionState
        var visibleHeight = 0
        val flight =
            Flight(
                "route",
                "YYZ",
                "BCN",
                "2027-02-01",
                "Aeroplan",
                16000,
                "Economy",
                "Toronto",
                "Barcelona",
                43.67594,
                -79.62942,
                41.2971,
                2.07846,
            )
        compose.setContent {
            camera = rememberCameraPositionState()
            visibleHeight = with(LocalDensity.current) { 180.dp.roundToPx() }
            Box(Modifier.size(width = 360.dp, height = 360.dp)) {
                FlightMap(
                    emptyList(),
                    flight,
                    camera,
                    onDestination = {},
                    onFocused = { fitted.set(true) },
                    bottomPadding = 180.dp,
                )
            }
        }
        compose.waitUntil(60_000) { fitted.get() }
        compose.runOnIdle {
            val visible = requireNotNull(camera.projection).visibleRegion.latLngBounds
            assertTrue(
                "Origin must fit the rendered viewport",
                visible.contains(LatLng(flight.originLatitude, flight.originLongitude)),
            )
            assertTrue(
                "Destination must fit the rendered viewport",
                visible.contains(LatLng(flight.destinationLatitude, flight.destinationLongitude)),
            )
            assertTrue("Curved route must also fit", visible.contains(LatLng(50.4, -38.8)))
            for (coordinate in
                listOf(
                    LatLng(flight.originLatitude, flight.originLongitude),
                    LatLng(flight.destinationLatitude, flight.destinationLongitude),
                    LatLng(50.4, -38.8),
                )) {
                val pixel = requireNotNull(camera.projection).toScreenLocation(coordinate)
                assertTrue(
                    "Route must stay above the reserved detail card",
                    pixel.y in 0..visibleHeight,
                )
            }
        }
    }
}
