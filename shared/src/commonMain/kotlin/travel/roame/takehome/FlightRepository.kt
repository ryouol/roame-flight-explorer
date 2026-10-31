package travel.roame.takehome

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class RepositoryState(
    val cached: List<Flight> = emptyList(),
    val saved: List<Flight> = emptyList(),
    val lastRefresh: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val actionError: String? = null,
    val pendingIds: Set<String> = emptySet(),
) {
    val hasCache: Boolean get() = lastRefresh != null
}

class FlightRepository(
    private val source: FlightSource,
    private val persistence: FlightPersistence,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> String = { Clock.System.now().toString() },
) {
    private val mutableState = MutableStateFlow(RepositoryState())
    val state = mutableState.asStateFlow()
    private val databaseMutex = Mutex()
    private val refreshMutex = Mutex()

    suspend fun load(): Boolean = try {
        databaseMutex.withLock {
            val stored = withContext(worker) { persistence.read() }
            mutableState.update { it.copy(cached = stored.cached, saved = stored.saved,
                lastRefresh = stored.refreshedAt, loading = false, refreshError = null) }
        }
        true
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (_: Exception) {
        mutableState.update { it.copy(loading = false, refreshError = "Could not read local flight data. Please retry.") }
        false
    }

    suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        mutableState.update { it.copy(refreshing = true, refreshError = null) }
        try {
            val flights = withContext(worker) { source.fetch() }
            databaseMutex.withLock {
                val timestamp = now()
                withContext(worker) { persistence.replaceCache(flights, timestamp) }
                mutableState.update { it.copy(cached = flights, lastRefresh = timestamp, refreshError = null) }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            println("Flight refresh failed: ${error::class.simpleName}: ${error.message}")
            mutableState.update { it.copy(refreshError = if (it.hasCache)
                "Refresh unavailable. Showing saved device data; it may be out of date."
                else "Flights could not be loaded. Check that the local API is running, then retry.") }
        } finally {
            mutableState.update { it.copy(refreshing = false) }
            refreshMutex.unlock()
        }
    }

    suspend fun save(flight: Flight) = mutate(flight.id, "save") {
        persistence.save(flight)
        mutableState.update { it.copy(saved = (it.saved.filterNot { saved -> saved.id == flight.id } + flight).sortedWith(flightOrder)) }
    }

    suspend fun remove(id: String) = mutate(id, "remove") {
        persistence.remove(id)
        mutableState.update { it.copy(saved = it.saved.filterNot { flight -> flight.id == id }) }
    }

    private suspend fun mutate(id: String, action: String, operation: () -> Unit) {
        // The mutex keeps database writes and their corresponding state updates in order.
        databaseMutex.withLock {
            mutableState.update { it.copy(pendingIds = it.pendingIds + id, actionError = null) }
            try { withContext(worker) { operation() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(actionError = "Could not $action this flight. Your saved flights have not changed. Please try again.") }
            } finally {
                mutableState.update { it.copy(pendingIds = it.pendingIds - id) }
            }
        }
    }

    fun dismissActionError() { mutableState.update { it.copy(actionError = null) } }
    fun close() { source.close(); persistence.close() }
}
