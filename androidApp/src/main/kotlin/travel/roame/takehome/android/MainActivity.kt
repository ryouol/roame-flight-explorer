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
        setContent { FlightTheme { val model: FlightViewModel = viewModel(); FlightExplorer(model.store) } }
    }
}

@Composable
private fun FlightTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF76D7C4), secondary = Color(0xFFA2D5CF))
    else lightColorScheme(primary = Color(0xFF006B60), secondary = Color(0xFF416B65), surface = Color(0xFFF7FBF9))
    MaterialTheme(colorScheme = colors, content = content)
}
