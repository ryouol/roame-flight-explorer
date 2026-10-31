package travel.roame.takehome

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import travel.roame.takehome.db.FlightDatabase

actual fun testDriver(name: String): SqlDriver {
    val file = File(System.getProperty("java.io.tmpdir"), "roame-$name.db")
    val existed = file.exists()
    return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").also { if (!existed) FlightDatabase.Schema.create(it) }
}

actual fun deleteTestDatabase(name: String) {
    File(System.getProperty("java.io.tmpdir"), "roame-$name.db").delete()
}
