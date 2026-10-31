package travel.roame.takehome.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test

/** Separate instrumentation invocations exercise a real process restart with the host API stopped. */
class OfflineWalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun walkthrough() {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("offlinePhase")
        assumeNotNull(phase) // Opt in with scripts/android_walkthrough.py.
        if (phase != "offline") InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("flights.db")
        ActivityScenario.launch(MainActivity::class.java).use {
            if (phase == "cold") {
                awaitText("Flights are unavailable")
                compose.onNodeWithText("Retry").assertIsDisplayed()
                pause()
                return
            }
            awaitText("1000 flights")
            if (phase == "offline") awaitText("Refresh unavailable. Showing saved device data; it may be out of date.")
            pause()
            compose.onNode(hasSetTextAction() and hasText("From")).performTextInput("yyz")
            compose.onNode(hasSetTextAction() and hasText("To")).performTextInput("bcn")
            awaitText("YYZ → BCN")
            pause()
            if (phase == "online") {
                compose.onAllNodesWithText("Details").onFirst().performClick()
                awaitText("Show on map")
                pause()
                compose.onNodeWithText("Show on map").performClick()
                awaitText("Close selection")
                pause()
                compose.onAllNodesWithText("Save").onFirst().performClick()
                awaitText("Saved (1)")
            }
            compose.onNodeWithText("Saved (1)").performClick()
            compose.onNodeWithText("YYZ → BCN").assertIsDisplayed()
            pause()
            if (phase == "offline") {
                compose.onNodeWithText("Remove").performClick()
                awaitText("Your next trip starts here")
                pause()
            }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pause() {
        if (InstrumentationRegistry.getArguments().getString("recording") == "true") Thread.sleep(2_000)
    }
}
