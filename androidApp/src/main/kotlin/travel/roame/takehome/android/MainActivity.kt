package travel.roame.takehome.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import travel.roame.takehome.createFlightStore

class FlightViewModel(application: Application) : AndroidViewModel(application) {
    val store = createFlightStore(application, BuildConfig.FLIGHTS_BASE_URL)

    override fun onCleared() = store.close()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlightTheme {
                val model: FlightViewModel = viewModel()
                FlightExplorer(model.store)
            }
        }
    }
}

@Composable
private fun FlightTheme(content: @Composable () -> Unit) {
    val colors =
        if (isSystemInDarkTheme())
            darkColorScheme(
                primary = Color(0xFF76D7C4),
                onPrimary = Color(0xFF00382F),
                primaryContainer = Color(0xFF005047),
                secondary = Color(0xFFA2D5CF),
                secondaryContainer = Color(0xFF264D47),
                background = Color(0xFF101917),
                surface = Color(0xFF101917),
                surfaceContainer = Color(0xFF1B2522),
                surfaceContainerLow = Color(0xFF15201D),
                surfaceContainerHigh = Color(0xFF26302D),
            )
        else
            lightColorScheme(
                primary = Color(0xFF006B60),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFBDECE2),
                secondary = Color(0xFF416B65),
                secondaryContainer = Color(0xFFD3EAE3),
                background = Color(0xFFF7FBF9),
                surface = Color(0xFFF7FBF9),
                surfaceContainer = Color(0xFFEAF2ED),
                surfaceContainerLow = Color.White,
                surfaceContainerHigh = Color(0xFFE1ECE5),
            )
    MaterialTheme(colorScheme = colors, content = content)
}
