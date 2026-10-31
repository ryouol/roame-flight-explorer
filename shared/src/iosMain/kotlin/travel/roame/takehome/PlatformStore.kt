package travel.roame.takehome

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import travel.roame.takehome.db.FlightDatabase

fun createFlightStore(baseUrl: String): FlightStore {
    val persistence = LazyFlightPersistence {
        SqlFlightPersistence(NativeSqliteDriver(FlightDatabase.Schema, "flights.db"))
    }
    val client = HttpClient(Darwin) { install(HttpTimeout) { requestTimeoutMillis = 10_000 } }
    return FlightStore(FlightRepository(FlightApi(client, baseUrl), persistence))
}
