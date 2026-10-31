package travel.roame.takehome.android

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBoundsTest {
    @Test fun includesNorthernArcBeyondBothAirports() {
        val bounds = routeBounds(LatLng(50.0, -80.0), LatLng(50.0, 80.0))
        assertTrue(bounds.contains(LatLng(50.0, -80.0)))
        assertTrue(bounds.contains(LatLng(50.0, 80.0)))
        assertTrue(bounds.contains(LatLng(81.7, 0.0)))
    }

    @Test fun dateLineRouteUsesTheShortLongitudeSpan() {
        val bounds = routeBounds(LatLng(30.0, 170.0), LatLng(30.0, -170.0))
        assertTrue(bounds.contains(LatLng(30.2, 180.0)))
        assertTrue(bounds.southwest.longitude > bounds.northeast.longitude)
    }
}
