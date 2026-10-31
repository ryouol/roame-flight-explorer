package travel.roame.takehome.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Run with the local Python API available. Each test owns fresh disposable app data. */
class FlightFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launch() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("flights.db")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodesWithText("1000 flights").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun close() { scenario.close() }

    @Test fun searchSaveAndRestoreOnRecreation() {
        compose.onNode(hasSetTextAction() and hasText("From")).performTextInput("  yyz ")
        compose.onNode(hasSetTextAction() and hasText("To")).performTextInput("bcn")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("YYZ → BCN").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Save").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Saved (1)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved (1)").performClick()
        compose.onNodeWithText("YYZ → BCN").assertIsDisplayed()
        scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Saved (1)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved (1)").performClick()
        compose.onNodeWithText("YYZ → BCN").assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Saved (0)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your next trip starts here").assertIsDisplayed()
    }

    @Test fun filterValidationEmptyResultsAndReset() {
        compose.onNodeWithText("Filters").performClick()
        compose.onNode(hasSetTextAction() and hasText("Maximum points")).performTextInput("0")
        compose.onNode(hasSetTextAction() and hasText("Maximum points")).performImeAction()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        compose.onNodeWithText("Apply filters").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No matching flights").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reset").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1000 flights").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun filtersCombineCabinProgramsPointsAndDates() {
        compose.onNodeWithText("Filters").performClick()
        fun field(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("filters-sheet")))
        field("Business").performScrollTo().performClick()
        field("Aeroplan").performScrollTo().performClick()
        field("Avios").performScrollTo().performClick()
        field("Maximum points").performScrollTo().performTextInput("50000")
        field("Maximum points").performImeAction()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        field("Apply filters").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("42 flights").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Filters").performClick()
        field("From").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        compose.onNode(hasSetTextAction() and hasContentDescription("MM/DD/YYYY", substring = true)).performTextReplacement("03012027")
        compose.onNodeWithText("Choose date").performClick()
        field("Apply filters").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("20 flights").fetchSemanticsNodes().isNotEmpty() }
    }
}
