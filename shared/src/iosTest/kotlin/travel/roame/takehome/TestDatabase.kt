@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package travel.roame.takehome

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import travel.roame.takehome.db.FlightDatabase

actual fun testDriver(name: String): SqlDriver =
    NativeSqliteDriver(
        FlightDatabase.Schema,
        "roame-$name.db",
        onConfiguration = {
            it.copy(extendedConfig = it.extendedConfig.copy(basePath = NSTemporaryDirectory()))
        },
    )

actual fun deleteTestDatabase(name: String) {
    for (suffix in listOf("", "-wal", "-shm")) {
        NSFileManager.defaultManager.removeItemAtPath(
            "${NSTemporaryDirectory()}roame-$name.db$suffix",
            null,
        )
    }
}
