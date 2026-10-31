package travel.roame.takehome

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

const val FIRST_DATE = "2027-02-01"
const val LAST_DATE = "2027-04-01"

@Serializable
data class Flight(
    val id: String,
    val origin: String,
    val destination: String,
    val departureDate: String,
    val program: String,
    val points: Int,
    val cabin: String,
    val originCity: String,
    val destinationCity: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
)

@Serializable
data class FlightsResponse(val flights: List<Flight>)

data class FlightFilters(
    val origin: String = "",
    val destination: String = "",
    val startDate: String = FIRST_DATE,
    val endDate: String = LAST_DATE,
    val cabin: String = "",
    val maximumPoints: Int? = null,
    val programs: Set<String> = emptySet(),
) {
    fun validationError(): String? {
        val start = if (startDate.isBlank()) null else runCatching { LocalDate.parse(startDate) }.getOrNull()
            ?: return "Enter a valid departure start date."
        val end = if (endDate.isBlank()) null else runCatching { LocalDate.parse(endDate) }.getOrNull()
            ?: return "Enter a valid departure end date."
        if (start != null && end != null && start > end) return "Start date must be on or before end date."
        if (maximumPoints != null && maximumPoints < 0) return "Maximum points must be zero or greater."
        return null
    }

    val activeLabels: List<String> get() = buildList {
        if (origin.isNotBlank()) add("From: ${origin.trim()}")
        if (destination.isNotBlank()) add("To: ${destination.trim()}")
        if (startDate != FIRST_DATE || endDate != LAST_DATE) add("${startDate.ifBlank { "Any date" }} – ${endDate.ifBlank { "Any date" }}")
        if (cabin.isNotBlank()) add(cabin)
        maximumPoints?.let { add("Up to $it points") }
        addAll(programs.sorted())
    }
}

data class DestinationGroup(
    val airport: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val flights: List<Flight>,
    val count: Int,
    val minimumPoints: Int,
)

val flightOrder = compareBy<Flight> { it.departureDate }.thenBy { it.points }.thenBy { it.id }

fun filterFlights(flights: List<Flight>, filters: FlightFilters): List<Flight> {
    val origin = filters.origin.trim()
    val destination = filters.destination.trim()
    return flights.filter { flight ->
        (origin.isEmpty() || flight.origin.contains(origin, true) || flight.originCity.contains(origin, true)) &&
            (destination.isEmpty() || flight.destination.contains(destination, true) || flight.destinationCity.contains(destination, true)) &&
            (filters.startDate.isBlank() || flight.departureDate >= filters.startDate) &&
            (filters.endDate.isBlank() || flight.departureDate <= filters.endDate) &&
            (filters.cabin.isBlank() || flight.cabin == filters.cabin) &&
            (filters.maximumPoints == null || flight.points <= filters.maximumPoints) &&
            (filters.programs.isEmpty() || flight.program in filters.programs)
    }.sortedWith(flightOrder)
}

fun aggregateDestinations(flights: List<Flight>): List<DestinationGroup> =
    flights.groupBy { it.destination }.map { (airport, matches) ->
        val first = matches.first()
        DestinationGroup(
            airport = airport,
            city = first.destinationCity,
            latitude = first.destinationLatitude,
            longitude = first.destinationLongitude,
            flights = matches.sortedWith(flightOrder),
            count = matches.size,
            minimumPoints = matches.minOf { it.points },
        )
    }.sortedBy { it.airport }
