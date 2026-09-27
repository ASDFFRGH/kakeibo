import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val defaultApiBaseUrl = "http://10.0.2.2:8080/api/v1/"
val apiBaseUrl = providers.gradleProperty("API_BASE_URL").orElse(defaultApiBaseUrl).get()
val apiBaseUri = runCatching { URI(apiBaseUrl) }.getOrNull()
require(
    apiBaseUri != null &&
        apiBaseUri.scheme in setOf("http", "https") &&
        !apiBaseUri.host.isNullOrBlank() &&
        apiBaseUri.rawQuery == null &&
        apiBaseUri.rawFragment == null &&
        apiBaseUri.path.endsWith("/api/v1/"),
) {
    "API_BASE_URL must be an absolute http(s) URL ending in /api/v1/: $apiBaseUrl"
}

android {
    namespace = "jp.local.kakeibo"
    compileSdk = 35
    defaultConfig {
        applicationId = "jp.local.kakeibo"
        minSdk = 29
        targetSdk = 35
        versionCode = 20
        versionName = "1.20"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }
    signingConfigs {
        getByName("debug") {
            System.getenv("ANDROID_DEBUG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let {
                storeFile = file(it)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
