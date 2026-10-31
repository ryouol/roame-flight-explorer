package travel.roame.takehome.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import travel.roame.takehome.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun Int.pointsLabel() = NumberFormat.getIntegerInstance().format(this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightExplorer(store: FlightStore) {
    val state by store.state.collectAsStateWithLifecycle()
    var savedTab by rememberSaveable { mutableStateOf(false) }
    var mapMode by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<String?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var savedDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedFlightId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCardHeight by remember { mutableIntStateOf(0) }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(30.0, 0.0), 1.2f) }
    val listDetail = if (savedTab) state.saved.find { it.id == savedDetailId }
        else state.selectedFlight.takeIf { detailOpen && !mapMode }
    val select: (Flight) -> Unit = { store.selectFlight(it.id); focusedFlightId = null }
    val group = state.destinations.find { it.airport == destination }

    LaunchedEffect(savedDetailId, state.saved, state.loading) {
        if (!state.loading && savedDetailId != null && state.saved.none { it.id == savedDetailId }) {
            savedDetailId = null
        }
    }
    LaunchedEffect(destination, group) {
        if (destination != null && group == null) destination = null
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Column {
                Text(if (savedTab) "Saved flights" else "Flight Explorer", fontWeight = FontWeight.Bold)
                Text(if (savedTab) "Your shortlist, available offline" else "Find your next points getaway", style = MaterialTheme.typography.labelMedium)
            } }, actions = { TextButton(onClick = store::refresh, enabled = !state.refreshing && !state.loading) { Text("Refresh") } })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = !savedTab, onClick = { savedTab = false }, icon = { Text("✈") }, label = { Text("Discover") })
                NavigationBarItem(selected = savedTab, onClick = { savedTab = true }, icon = { Text("♡") }, label = { Text("Saved (${state.saved.size})") })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.refreshError?.let { message ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = store::refresh, enabled = !state.refreshing) { Text("Retry") }
                    }
                }
            }
            if (!savedTab) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AirportSearchField("From", state.filters.origin, store::setOrigin, Modifier.weight(1f))
                    AirportSearchField("To", state.filters.destination, store::setDestination, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.flights.size} flights", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { filtersOpen = true }) { Text("Filters") }
                    TextButton(onClick = store::resetFilters) { Text("Reset") }
                }
                if (state.filters.activeLabels.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.filters.activeLabels) { label -> SuggestionChip(onClick = { filtersOpen = true }, label = { Text(label) }) }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SegmentedButton(selected = !mapMode, onClick = { mapMode = false; detailOpen = state.selectedFlight != null }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("List") }
                    SegmentedButton(selected = mapMode, onClick = { mapMode = true; detailOpen = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Map") }
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                savedTab -> FlightList(state.saved, state, onSelect = { savedDetailId = it.id }, onToggle = { toggleSave(store, state, it) }, emptyTitle = "Your next trip starts here", emptyMessage = "Save flights from Discover to find them here, even offline.")
                !state.hasCache && state.refreshing -> EmptyState("Finding flights", "Connecting to the local flight API…")
                !state.hasCache -> EmptyState("Flights are unavailable", "Start the local API and use Retry or Refresh.")
                state.flights.isEmpty() -> EmptyState(if (state.cacheCount == 0) "No flights available" else "No matching flights", if (state.cacheCount == 0) "The API returned an empty flight list. Try refreshing later." else "Try a different route or reset your filters.")
                mapMode -> Box(Modifier.fillMaxSize()) {
                    FlightMap(state.destinations, state.selectedFlight, camera, onDestination = { destination = it },
                        focusSelection = selectedCardHeight > 0 && focusedFlightId != state.selectedFlight?.id, onFocused = { focusedFlightId = it },
                        bottomPadding = if (state.selectedFlight != null) with(LocalDensity.current) { selectedCardHeight.toDp() } + 40.dp else 0.dp)
                    state.selectedFlight?.let { flight ->
                        Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 28.dp).onSizeChanged { selectedCardHeight = it.height }, shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, shadowElevation = 4.dp) {
                            Column { FlightCard(flight, state, onToggle = { toggleSave(store, state, flight) }); TextButton(onClick = { store.selectFlight(null) }) { Text("Close selection") } }
                        }
                    }
                }
                else -> FlightList(state.flights, state, onSelect = { select(it); detailOpen = true }, onToggle = { toggleSave(store, state, it) })
            }
        }
    }
    if (filtersOpen) FilterSheet(state.filters, onDismiss = { filtersOpen = false }, onApply = store::applyFilters)
    if (group != null && !savedTab) ModalBottomSheet(onDismissRequest = { destination = null }) {
        Text("${group.city} · ${group.airport}", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            FlightList(group.flights, state, onSelect = { select(it); destination = null }, onToggle = { toggleSave(store, state, it) })
        }
    }
    if (listDetail != null) ModalBottomSheet(
        onDismissRequest = { detailOpen = false; savedDetailId = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val detailCamera = rememberCameraPositionState()
        Box(Modifier.fillMaxWidth().height(220.dp)) { FlightMap(emptyList(), listDetail, detailCamera, onDestination = {}) }
        FlightCard(listDetail, state, onToggle = { toggleSave(store, state, listDetail) })
        if (!savedTab) TextButton(onClick = { mapMode = true; detailOpen = false }) { Text("Show on map") }
        Spacer(Modifier.height(24.dp))
    }
    state.actionError?.let { error -> AlertDialog(onDismissRequest = store::dismissActionError, title = { Text("Change not saved") }, text = { Text(error) }, confirmButton = { TextButton(onClick = store::dismissActionError) { Text("OK") } }) }
}

@Composable
private fun AirportSearchField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("Code or city") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier,
    )
}

private fun toggleSave(store: FlightStore, state: FlightScreenState, flight: Flight) {
    if (state.isSaved(flight.id)) store.removeFlight(flight.id) else store.saveFlight(flight)
}

@Composable
private fun FlightList(flights: List<Flight>, state: FlightScreenState, onSelect: (Flight) -> Unit, onToggle: (Flight) -> Unit,
    emptyTitle: String = "No matching flights", emptyMessage: String = "Try resetting your filters.") {
    if (flights.isEmpty()) EmptyState(emptyTitle, emptyMessage)
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(flights, key = { it.id }) { flight ->
            ElevatedCard(onClick = { onSelect(flight) }, modifier = Modifier.fillMaxWidth()) {
                FlightCard(flight, state, onSelect = { onSelect(flight) }, onToggle = { onToggle(flight) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlightCard(flight: Flight, state: FlightScreenState, onToggle: () -> Unit, onSelect: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${flight.origin} → ${flight.destination}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${flight.points.pointsLabel()} pts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text("${flight.originCity} to ${flight.destinationCity}", style = MaterialTheme.typography.bodyMedium)
        Text("${flight.departureDate} · ${flight.cabin}", style = MaterialTheme.typography.bodyMedium)
        Text(flight.program, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (onSelect != null) TextButton(onClick = onSelect) { Text("Details") }
            FilledTonalButton(onClick = onToggle, enabled = !state.isPending(flight.id), modifier = Modifier.semantics {
                contentDescription = "${if (state.isSaved(flight.id)) "Remove saved" else "Save"} flight ${flight.origin} to ${flight.destination} on ${flight.departureDate}"
            }) {
                Text(if (state.isPending(flight.id)) "Saving…" else if (state.isSaved(flight.id)) "Remove" else "Save")
            }
        }
    }
}

@Composable
internal fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(filters: FlightFilters, onDismiss: () -> Unit, onApply: (String, String, String, String, List<String>) -> String?) {
    val focus = LocalFocusManager.current
    var start by remember { mutableStateOf(filters.startDate) }
    var end by remember { mutableStateOf(filters.endDate) }
    var cabin by remember { mutableStateOf(filters.cabin) }
    var maximum by remember { mutableStateOf(filters.maximumPoints?.toString() ?: "") }
    var programs by remember { mutableStateOf(filters.programs) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().testTag("filters-sheet").verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Refine your flight", style = MaterialTheme.typography.headlineSmall)
            Text("Departure dates include both endpoints.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editingStart = true }, modifier = Modifier.weight(1f)) { Column { Text("From"); Text(start.ifBlank { "Any date" }) } }
                OutlinedButton(onClick = { editingStart = false }, modifier = Modifier.weight(1f)) { Column { Text("Through"); Text(end.ifBlank { "Any date" }) } }
            }
            Text("Cabin", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("", "Economy", "Premium Economy", "Business").forEach { option ->
                    FilterChip(selected = cabin == option, onClick = { cabin = option }, label = { Text(option.ifBlank { "Any cabin" }) })
                }
            }
            OutlinedTextField(maximum, { maximum = it }, label = { Text("Maximum points") }, placeholder = { Text("No limit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Loyalty programs · any selected", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Aeroplan", "Avios", "Flying Blue", "Mileage Plan", "MileagePlus").forEach { option ->
                    FilterChip(selected = option in programs, onClick = { programs = if (option in programs) programs - option else programs + option }, label = { Text(option) })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { error = onApply(start.trim(), end.trim(), cabin, maximum, programs.toList()); if (error == null) onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Apply filters") }
            Spacer(Modifier.height(16.dp))
        }
    }
    editingStart?.let { isStart ->
        val date = if (isStart) start else end
        val picker = rememberDatePickerState(initialSelectedDateMillis = runCatching { dateFormat.parse(date)?.time }.getOrNull())
        DatePickerDialog(onDismissRequest = { editingStart = null }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { if (isStart) start = dateFormat.format(Date(it)) else end = dateFormat.format(Date(it)) }
                editingStart = null
            }) { Text("Choose date") }
        }, dismissButton = { TextButton(onClick = { editingStart = null }) { Text("Cancel") } }) {
            DatePicker(state = picker)
        }
    }
}
