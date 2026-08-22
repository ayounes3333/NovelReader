plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "my.noveldokusha.tooling.quick_setup"
}

dependencies {
    implementation(projects.core)
    implementation(projects.coreui)
    implementation(projects.strings)
    implementation(projects.data)
    implementation(projects.tooling.localDatabase)
    implementation(projects.tooling.backupCreate)
    implementation(projects.tooling.backupRestore)
    implementation(projects.networking)

    // Timber for logging
    implementation(libs.timber)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // NanoHTTPD embedded server
    implementation(libs.nanohttpd)

    // QR Code generation
    implementation(libs.zxing.android.embedded)

    // CameraX for QR scanning
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit barcode scanning
    implementation(libs.mlkit.barcode.scanning)

    // OkHttp client (for target-side HTTP requests)
    implementation(libs.okhttp)

    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(libs.compose.androidx.ui)
    implementation(libs.compose.androidx.activity)
    implementation(libs.compose.androidx.lifecycle.viewmodel)
    implementation(libs.compose.androidx.material.icons.extended)
    implementation(libs.compose.material3.android)

    // Dependency injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.workmanager)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)
}
