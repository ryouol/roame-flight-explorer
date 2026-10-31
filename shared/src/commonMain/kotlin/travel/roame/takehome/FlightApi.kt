package travel.roame.takehome

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

internal val flightJson = Json { ignoreUnknownKeys = true }

interface FlightSource {
    suspend fun fetch(): List<Flight>
    fun close() {}
}

class FlightApi(private val client: HttpClient, private val baseUrl: String) : FlightSource {
    override suspend fun fetch(): List<Flight> {
        val response = client.get("${baseUrl.trimEnd('/')}/flights")
        check(response.status == HttpStatusCode.OK) { "Unexpected response: ${response.status.value}" }
        val flights = flightJson.decodeFromString<FlightsResponse>(response.bodyAsText()).flights
        require(flights.map { it.id }.toSet().size == flights.size) { "Duplicate flight IDs" }
        flights.forEach {
            require(it.id.isNotBlank() && it.origin.isNotBlank() && it.destination.isNotBlank())
            LocalDate.parse(it.departureDate)
            require(it.points >= 0)
            require(it.originLatitude in -90.0..90.0 && it.destinationLatitude in -90.0..90.0)
            require(it.originLongitude in -180.0..180.0 && it.destinationLongitude in -180.0..180.0)
        }
        return flights
    }

    override fun close() = client.close()
}
