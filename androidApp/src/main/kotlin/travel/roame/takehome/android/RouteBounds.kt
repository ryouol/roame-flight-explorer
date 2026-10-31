package travel.roame.takehome.android

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlin.math.*

/** Include the curved route, whose highest latitude can be far beyond either airport. */
internal fun routeBounds(origin: LatLng, destination: LatLng): LatLngBounds {
    val bounds = LatLngBounds.builder().include(origin).include(destination)
    val lat1 = Math.toRadians(origin.latitude)
    val lat2 = Math.toRadians(destination.latitude)
    val lon1 = Math.toRadians(origin.longitude)
    val lon2 = Math.toRadians(destination.longitude)
    val angle = acos((sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(lon2 - lon1)).coerceIn(-1.0, 1.0))
    val denominator = sin(angle)
    if (abs(denominator) < 1e-8) return bounds.build()
    for (step in 1 until 64) {
        val fraction = step / 64.0
        val a = sin((1 - fraction) * angle) / denominator
        val b = sin(fraction * angle) / denominator
        val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
        val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
        val z = a * sin(lat1) + b * sin(lat2)
        bounds.include(LatLng(Math.toDegrees(atan2(z, hypot(x, y))), Math.toDegrees(atan2(y, x))))
    }
    return bounds.build()
}
