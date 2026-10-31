import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val localSecrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsKey = localSecrets.getProperty("MAPS_API_KEY", System.getenv("MAPS_API_KEY") ?: "")
android {
    buildToolsVersion = "37.0.0"
    namespace = "travel.roame.takehome.android"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "travel.roame.takehome.android"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("boolean", "MAPS_CONFIGURED", mapsKey.isNotBlank().toString())
        buildConfigField("String", "FLIGHTS_BASE_URL", "\"http://10.0.2.2:8080\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("com.google.maps.android:maps-compose:8.4.0")
}
