import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "travel.roame.takehome.shared"
        compileSdk { version = release(37) { minorApiLevel = 0 } }
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("app.cash.sqldelight:runtime:2.4.1")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.6.1")
            implementation("app.cash.sqldelight:android-driver:2.4.1")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.6.1")
            implementation("app.cash.sqldelight:native-driver:2.4.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.6.1")
        }
        getByName("androidHostTest").dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.4.1")
        }
    }
}

sqldelight {
    databases {
        create("FlightDatabase") { packageName.set("travel.roame.takehome.db") }
    }
}

