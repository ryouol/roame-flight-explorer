package travel.roame.takehome

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import travel.roame.takehome.db.FlightDatabase

fun createFlightStore(context: Context, baseUrl: String): FlightStore {
    val applicationContext = context.applicationContext
    val persistence = LazyFlightPersistence {
        SqlFlightPersistence(AndroidSqliteDriver(FlightDatabase.Schema, applicationContext, "flights.db"))
    }
    val client = HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 10_000 } }
    return FlightStore(FlightRepository(FlightApi(client, baseUrl), persistence))
}
