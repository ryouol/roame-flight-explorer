package travel.roame.takehome

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.*
import kotlin.random.Random

expect fun testDriver(name: String): SqlDriver
expect fun deleteTestDatabase(name: String)

fun flight(id: String = "a", destination: String = "LHR", date: String = FIRST_DATE,
    points: Int = 25_000, program: String = "Aeroplan", cabin: String = "Economy") =
    Flight(id, "YYZ", destination, date, program, points, cabin, "Toronto", "London", 43.6, -79.6, 51.4, -0.4)

class FilteringTests {
    private val flights = listOf(flight(), flight("b", date = LAST_DATE, points = 50_000, program = "Avios"),
        flight("c", destination = "HND", date = "2027-03-01", cabin = "Business"))

    @Test fun normalizesCodesCitiesAndWhitespace() {
        assertEquals(flights.take(2), filterFlights(flights, FlightFilters(origin = "  ToRoN ", destination = " lhR ")))
    }
    @Test fun emptyFiltersIncludeAllAndOrderIsStable() {
        assertEquals(listOf("a", "c", "b"), filterFlights(flights.reversed(), FlightFilters(startDate = "", endDate = "")).map { it.id })
    }
    @Test fun datesAndPointsAreInclusive() {
        assertEquals(listOf("b"), filterFlights(flights, FlightFilters(startDate = LAST_DATE, endDate = LAST_DATE, maximumPoints = 50_000)).map { it.id })
        assertTrue(filterFlights(flights, FlightFilters(startDate = LAST_DATE, maximumPoints = 49_999)).isEmpty())
    }
    @Test fun combinesCategoriesWithAndAndProgramsWithOr() {
        assertEquals(listOf("a", "b"), filterFlights(flights, FlightFilters(cabin = "Economy", programs = setOf("Aeroplan", "Avios"))).map { it.id })
        assertEquals(listOf("a"), filterFlights(flights, FlightFilters(cabin = "Economy", maximumPoints = 25_000, programs = setOf("Aeroplan", "Avios"))).map { it.id })
    }
    @Test fun validatesDatesAndPoints() {
        assertNull(FlightFilters(startDate = "", endDate = "").validationError())
        assertNotNull(FlightFilters(startDate = LAST_DATE, endDate = FIRST_DATE).validationError())
        assertNotNull(FlightFilters(startDate = "bad").validationError())
        assertNotNull(FlightFilters(maximumPoints = -1).validationError())
    }
    @Test fun aggregatesOnlyMatchingFlightsByAirportNotCity() {
        val groups = aggregateDestinations(filterFlights(flights, FlightFilters(maximumPoints = 25_000)))
        assertEquals(2, groups.size)
        assertEquals(1, groups.first { it.airport == "LHR" }.count)
        assertEquals(25_000, groups.first { it.airport == "LHR" }.minimumPoints)
    }
}

class PersistenceTests {
    @Test fun reopensCacheAndIndependentSavedSnapshots() {
        val name = "reopen-${Random.nextInt(Int.MAX_VALUE)}"
        var store = SqlFlightPersistence(testDriver(name))
        try {
            assertNull(store.read().refreshedAt)
            store.replaceCache(listOf(flight()), "first")
            store.save(flight())
            store.save(flight(points = 35_000))
            store.close()
            store = SqlFlightPersistence(testDriver(name))
            assertEquals("first", store.read().refreshedAt)
            assertEquals(1, store.read().saved.size)
            assertEquals(35_000, store.read().saved.single().points)
            store.replaceCache(emptyList(), "second")
            assertTrue(store.read().cached.isEmpty())
            assertEquals("second", store.read().refreshedAt)
            assertEquals(1, store.read().saved.size)
            store.remove("a")
            store.close()
            store = SqlFlightPersistence(testDriver(name))
            assertTrue(store.read().saved.isEmpty())
            assertEquals("second", store.read().refreshedAt)
        } finally { store.close(); deleteTestDatabase(name) }
    }

    @Test fun failedCacheTransactionRollsBackRowsAndMetadata() {
        val name = "rollback-${Random.nextInt(Int.MAX_VALUE)}"
        val store = SqlFlightPersistence(testDriver(name))
        try {
            store.replaceCache(listOf(flight()), "old")
            assertFails { store.replaceCache(listOf(flight("duplicate"), flight("duplicate")), "new") }
            assertEquals(listOf(flight()), store.read().cached)
            assertEquals("old", store.read().refreshedAt)
        } finally { store.close(); deleteTestDatabase(name) }
    }
}

internal class FakePersistence : FlightPersistence {
    var data = PersistedFlights(emptyList(), emptyList(), null)
    var failWrites = false
    override fun read() = data
    override fun replaceCache(flights: List<Flight>, refreshedAt: String) {
        check(!failWrites); data = data.copy(cached = flights, refreshedAt = refreshedAt)
    }
    override fun save(flight: Flight) {
        check(!failWrites); data = data.copy(saved = data.saved.filterNot { it.id == flight.id } + flight)
    }
    override fun remove(id: String) { check(!failWrites); data = data.copy(saved = data.saved.filterNot { it.id == id }) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryAndStoreTests {
    @Test fun savedFlightsStaySortedAcrossLoadSaveAndRemove() = runTest {
        val first = flight("first", points = 10_000)
        val second = flight("second", points = 20_000)
        val last = flight("last", date = LAST_DATE)
        val storage = FakePersistence().apply {
            data = PersistedFlights(emptyList(), listOf(last, first), "old")
        }
        val source = object : FlightSource { override suspend fun fetch() = emptyList<Flight>() }
        val repository = FlightRepository(source, storage, StandardTestDispatcher(testScheduler))
        repository.load()
        assertEquals(listOf(first, last), repository.state.value.saved)
        repository.save(second)
        assertEquals(listOf(first, second, last), repository.state.value.saved)
        repository.remove(first.id)
        assertEquals(listOf(second, last), repository.state.value.saved)
    }

    @Test fun failedRefreshPreservesCacheAndSuccessfulEmptyIsPersisted() = runTest {
        val storage = FakePersistence().apply { data = PersistedFlights(listOf(flight()), listOf(flight()), "old") }
        var fail = true
        val source = object : FlightSource { override suspend fun fetch(): List<Flight> { check(!fail); return emptyList() } }
        val repository = FlightRepository(source, storage, StandardTestDispatcher(testScheduler)) { "new" }
        repository.load(); repository.refresh()
        assertEquals(listOf(flight()), repository.state.value.cached)
        assertNotNull(repository.state.value.refreshError)
        fail = false; repository.refresh()
        assertTrue(repository.state.value.cached.isEmpty())
        assertTrue(repository.state.value.hasCache)
        assertNull(repository.state.value.refreshError)
        assertEquals(listOf(flight()), repository.state.value.saved)
        assertEquals("new", storage.data.refreshedAt)
    }

    @Test fun failedWritesNeverAppearSuccessful() = runTest {
        val storage = FakePersistence()
        val source = object : FlightSource { override suspend fun fetch() = listOf(flight()) }
        val repository = FlightRepository(source, storage, StandardTestDispatcher(testScheduler))
        repository.load(); repository.refresh(); repository.save(flight())
        storage.failWrites = true
        repository.remove("a")
        assertEquals(listOf(flight()), repository.state.value.saved)
        assertNotNull(repository.state.value.actionError)
        repository.save(flight("b"))
        assertEquals(1, repository.state.value.saved.size)
        val before = repository.state.value.lastRefresh
        repository.refresh()
        assertEquals(before, repository.state.value.lastRefresh)
        assertNotNull(repository.state.value.refreshError)
        assertTrue(repository.state.value.pendingIds.isEmpty())
    }

    @Test fun coldStartFailureIsNotAnEmptySuccess() = runTest {
        val source = object : FlightSource { override suspend fun fetch(): List<Flight> = error("offline") }
        val repository = FlightRepository(source, FakePersistence(), StandardTestDispatcher(testScheduler))
        repository.load(); repository.refresh()
        assertFalse(repository.state.value.hasCache)
        assertNotNull(repository.state.value.refreshError)
        assertFalse(repository.state.value.refreshing)
    }

    @Test fun latestFiltersSelectionResetAndSubscriptionCancellation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = object : FlightSource { override suspend fun fetch() = listOf(flight(), flight("b", destination = "HND")) }
        val store = FlightStore(FlightRepository(source, FakePersistence(), dispatcher), dispatcher, dispatcher)
        var emissions = 0
        val subscription = store.observe { emissions++ }
        try {
            advanceUntilIdle()
            store.selectFlight("a"); advanceUntilIdle()
            assertEquals("a", store.currentState.selectedFlight?.id)
            store.setOrigin("YYZ")
            assertEquals("YYZ", store.currentState.filters.origin)
            advanceUntilIdle()
            assertEquals("a", store.currentState.selectedFlight?.id)
            store.setDestination("LHR"); store.setDestination("HND"); advanceUntilIdle()
            assertEquals(listOf("b"), store.currentState.flights.map { it.id })
            assertNull(store.currentState.selectedFlight)
            store.resetFilters(); advanceUntilIdle()
            assertEquals(2, store.currentState.flights.size)
            assertNull(store.currentState.selectedFlight)
            assertTrue(store.currentState.filters.activeLabels.isEmpty())
            assertNotNull(store.applyFilters(FIRST_DATE, LAST_DATE, "", "2.5", emptyList()))
            subscription.cancel(); advanceUntilIdle()
            val previous = emissions
            store.setOrigin("nothing"); advanceUntilIdle()
            assertEquals(previous, emissions)
        } finally { store.close(); advanceUntilIdle() }
    }

    @Test fun concurrentRefreshesUseOneRequest() = runTest {
        val response = CompletableDeferred<List<Flight>>()
        var requests = 0
        val source = object : FlightSource { override suspend fun fetch(): List<Flight> { requests++; return response.await() } }
        val repository = FlightRepository(source, FakePersistence(), StandardTestDispatcher(testScheduler))
        repository.load()
        val first = launch { repository.refresh() }
        advanceUntilIdle()
        repository.refresh()
        response.complete(listOf(flight())); first.join()
        assertEquals(1, requests)
        assertEquals(1, repository.state.value.cached.size)
    }
}

class ApiTests {
    @Test fun decodesEnvelopeAndRejectsInvalidResponses() = runTest {
        for ((payload, status, succeeds) in listOf(
            Triple(flightJson.encodeToString(FlightsResponse(listOf(flight()))), HttpStatusCode.OK, true),
            Triple("{\"flights\":[]}", HttpStatusCode.OK, true),
            Triple("not json", HttpStatusCode.OK, false),
            Triple("{}", HttpStatusCode.OK, false),
            Triple("{\"flights\":[]}", HttpStatusCode.InternalServerError, false),
            Triple(flightJson.encodeToString(FlightsResponse(listOf(flight(), flight()))), HttpStatusCode.OK, false),
        )) {
            val api = FlightApi(HttpClient(MockEngine { request ->
                assertEquals("/flights", request.url.encodedPath)
                respond(payload, status)
            }), "http://localhost:8080")
            try { assertEquals(succeeds, runCatching { api.fetch() }.isSuccess) } finally { api.close() }
        }
    }
}
