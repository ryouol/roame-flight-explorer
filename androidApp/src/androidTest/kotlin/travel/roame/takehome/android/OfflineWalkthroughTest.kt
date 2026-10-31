package travel.roame.takehome.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
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
            if (phase == "online") {
                compose.onNodeWithText("Filters").performClick()
                compose.onNode(hasSetTextAction() and hasText("Maximum points")).performTextInput("0")
                compose.onNode(hasSetTextAction() and hasText("Maximum points")).performImeAction()
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
                compose.onNodeWithText("Apply filters").performScrollTo().performClick()
                awaitText("No matching flights")
                pause()
                compose.onNodeWithText("Reset").performClick()
                awaitText("1000 flights")
            }
            compose.onNode(hasSetTextAction() and hasText("From")).performTextInput("yyz")
            compose.onNode(hasSetTextAction() and hasText("To")).performTextInput("bcn")
            awaitText("YYZ → BCN")
            compose.onNode(hasSetTextAction() and hasText("To")).performImeAction()
            pause()
            if (phase == "online") {
                compose.onAllNodesWithText("Details").onFirst().performClick()
                awaitText("Show on map")
                compose.waitUntil(60_000) { compose.onAllNodesWithTag("map-loaded").fetchSemanticsNodes().isNotEmpty() }
                pause(5)
                compose.onNodeWithText("Show on map").performClick()
                awaitText("Close selection")
                compose.waitUntil(60_000) { compose.onAllNodesWithTag("map-loaded").fetchSemanticsNodes().isNotEmpty() }
                pause(5)
                compose.onNodeWithText("Close selection").performClick()
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.waitForIdle()
                val marker = device
                    .wait(Until.findObject(By.descContains("BCN · 8 flights")), 15_000)
                assertNotNull("The destination marker must be accessible", marker)
                marker.click()
                awaitText("Barcelona · BCN")
                pause()
                compose.onAllNodesWithText("Details").onFirst().performClick()
                awaitText("Close selection")
                pause(5)
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

    private fun pause(seconds: Int = 2) {
        if (InstrumentationRegistry.getArguments().getString("recording") == "true") Thread.sleep(seconds * 1_000L)
    }
}
