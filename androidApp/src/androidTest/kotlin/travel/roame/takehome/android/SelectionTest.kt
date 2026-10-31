package travel.roame.takehome.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import travel.roame.takehome.*

class SelectionTest {
    @get:Rule val compose = createComposeRule()
    private val flight =
        Flight(
            "selection",
            "YYZ",
            "BCN",
            FIRST_DATE,
            "Aeroplan",
            16000,
            "Economy",
            "Toronto",
            "Barcelona",
            43.67594,
            -79.62942,
            41.2971,
            2.07846,
        )
    private val response = AtomicReference(listOf(flight))
    private lateinit var store: FlightStore

    @Before
    fun launch() {
        val source =
            object : FlightSource {
                override suspend fun fetch() = response.get()
            }
        store = FlightStore(FlightRepository(source, SelectionPersistence()))
        compose.setContent { MaterialTheme { FlightExplorer(store) } }
        compose.waitUntil(10_000) { store.currentState.flights.size == 1 }
    }

    @After
    fun close() {
        store.close()
    }

    @Test
    fun removedSavedDetailStaysDismissedAfterSavingAgain() {
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { store.currentState.saved.size == 1 }
        compose.onNodeWithText("Saved (1)").performClick()
        compose.onNodeWithText("Details").performClick()
        compose.onAllNodesWithText("Remove").onLast().performClick()
        compose.waitUntil(10_000) { store.currentState.saved.isEmpty() }
        compose.onNodeWithText("Your next trip starts here").assertIsDisplayed()
        compose.onNodeWithText("Discover").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { store.currentState.saved.size == 1 }
        compose.onNodeWithText("Saved (1)").performClick()
        compose.onNodeWithText("YYZ → BCN").assertIsDisplayed()
        compose.onNodeWithTag("map-loaded").assertDoesNotExist()
        compose.onNodeWithTag("map-loading").assertDoesNotExist()
    }

    @Test
    fun removedDestinationStaysDismissedAfterRefreshRestoresIt() {
        compose.onNodeWithText("Map").performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag("map-loaded").fetchSemanticsNodes().isNotEmpty()
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val marker = device.wait(Until.findObject(By.descContains("BCN · 1 flights")), 15_000)
        requireNotNull(marker).click()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Barcelona · BCN").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Barcelona · BCN").assertIsDisplayed()
        response.set(emptyList())
        store.refresh()
        compose.waitUntil(10_000) { store.currentState.cacheCount == 0 }
        compose.onNodeWithText("Barcelona · BCN").assertDoesNotExist()
        response.set(listOf(flight))
        store.refresh()
        compose.waitUntil(10_000) { store.currentState.cacheCount == 1 }
        compose.onNodeWithText("Barcelona · BCN").assertDoesNotExist()
    }
}

/** UI tests control refresh responses without modifying the production API or database. */
private class SelectionPersistence : FlightPersistence {
    private var data = PersistedFlights(emptyList(), emptyList(), null)

    override fun read() = data

    override fun replaceCache(flights: List<Flight>, refreshedAt: String) {
        data = data.copy(cached = flights, refreshedAt = refreshedAt)
    }

    override fun save(flight: Flight) {
        data = data.copy(saved = data.saved.filterNot { it.id == flight.id } + flight)
    }

    override fun remove(id: String) {
        data = data.copy(saved = data.saved.filterNot { it.id == id })
    }
}
