package travel.roame.takehome

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.encodeToString
import travel.roame.takehome.db.FlightDatabase

data class PersistedFlights(
    val cached: List<Flight>,
    val saved: List<Flight>,
    val refreshedAt: String?,
)

interface FlightPersistence {
    fun read(): PersistedFlights
    fun replaceCache(flights: List<Flight>, refreshedAt: String)
    fun save(flight: Flight)
    fun remove(id: String)
    fun close() {}
}

class SqlFlightPersistence(private val driver: SqlDriver) : FlightPersistence {
    private val database = FlightDatabase(driver)
    private val queries = database.flightsQueries

    override fun read(): PersistedFlights = database.transactionWithResult {
        PersistedFlights(
            queries.allCached().executeAsList().map { flightJson.decodeFromString<Flight>(it) },
            queries.allSaved().executeAsList().map { flightJson.decodeFromString<Flight>(it) },
            queries.metadata().executeAsOneOrNull(),
        )
    }

    override fun replaceCache(flights: List<Flight>, refreshedAt: String) = database.transaction {
        queries.clearCache()
        flights.forEach { queries.insertCached(it.id, flightJson.encodeToString(it)) }
        queries.setMetadata(refreshedAt)
    }

    override fun save(flight: Flight) { queries.saveFlight(flight.id, flightJson.encodeToString(flight)) }
    override fun remove(id: String) { queries.removeSaved(id) }
    override fun close() = driver.close()
}

/** Open the database during the repository's background load, never while creating a UI. */
class LazyFlightPersistence(factory: () -> FlightPersistence) : FlightPersistence {
    private val lazyDelegate = lazy(factory)
    private val delegate by lazyDelegate
    override fun read() = delegate.read()
    override fun replaceCache(flights: List<Flight>, refreshedAt: String) = delegate.replaceCache(flights, refreshedAt)
    override fun save(flight: Flight) = delegate.save(flight)
    override fun remove(id: String) = delegate.remove(id)
    override fun close() { if (lazyDelegate.isInitialized()) delegate.close() }
}
