package travel.roame.takehome

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FlightScreenState(
    val filters: FlightFilters = FlightFilters(),
    val flights: List<Flight> = emptyList(),
    val destinations: List<DestinationGroup> = emptyList(),
    val saved: List<Flight> = emptyList(),
    val selectedFlight: Flight? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val hasCache: Boolean = false,
    val cacheCount: Int = 0,
    val lastRefresh: String? = null,
    val refreshError: String? = null,
    val actionError: String? = null,
    val pendingIds: Set<String> = emptySet(),
) {
    fun isSaved(id: String): Boolean = saved.any { it.id == id }
    fun isPending(id: String): Boolean = id in pendingIds
}

class FlightSubscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

@OptIn(ExperimentalCoroutinesApi::class)
class FlightStore(
    private val repository: FlightRepository,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + mainDispatcher)
    private val filters = MutableStateFlow(FlightFilters())
    private val selectedId = MutableStateFlow<String?>(null)
    private val mutableState = MutableStateFlow(FlightScreenState())
    val state = mutableState.asStateFlow()
    val currentState: FlightScreenState get() = state.value
    private var loaded = false

    init {
        scope.launch {
            combine(repository.state, filters, selectedId) { data, filter, selection -> Triple(data, filter, selection) }
                .mapLatest { (data, filter, selection) ->
                    withContext(worker) {
                        val flights = filterFlights(data.cached, filter)
                        FlightScreenState(filter, flights, aggregateDestinations(flights),
                            data.saved.sortedWith(flightOrder), flights.find { it.id == selection },
                            data.loading, data.refreshing, data.hasCache, data.cached.size,
                            data.lastRefresh, data.refreshError, data.actionError, data.pendingIds) to selection
                    }
                }.collect { (screen, requestedSelection) ->
                    if (requestedSelection != null && screen.selectedFlight == null) {
                        selectedId.compareAndSet(requestedSelection, null)
                    }
                    mutableState.value = screen
                }
        }
        refresh()
    }

    fun observe(onChange: (FlightScreenState) -> Unit): FlightSubscription =
        FlightSubscription(scope.launch { state.collect { onChange(it) } })

    fun setOrigin(value: String) { filters.update { it.copy(origin = value) } }
    fun setDestination(value: String) { filters.update { it.copy(destination = value) } }

    fun applyFilters(startDate: String, endDate: String, cabin: String, maximumPoints: String, programs: List<String>): String? {
        val points = maximumPoints.trim().takeIf { it.isNotEmpty() }
        val number = points?.toIntOrNull()
        if (points != null && number == null) return "Enter a whole number for maximum points."
        val next = filters.value.copy(startDate = startDate, endDate = endDate, cabin = cabin,
            maximumPoints = number, programs = programs.toSet())
        next.validationError()?.let { return it }
        filters.value = next
        return null
    }

    fun resetFilters() { filters.value = FlightFilters() }
    fun selectFlight(id: String?) { selectedId.value = id }
    fun refresh() { scope.launch { if (!loaded) loaded = repository.load(); if (loaded) repository.refresh() } }
    fun saveFlight(flight: Flight) { scope.launch { repository.save(flight) } }
    fun removeFlight(id: String) { scope.launch { repository.remove(id) } }
    fun dismissActionError() = repository.dismissActionError()

    fun close() {
        scope.cancel()
        // Close only once all in-flight reads/writes have finished cancellation.
        job.invokeOnCompletion { repository.close() }
    }
}
