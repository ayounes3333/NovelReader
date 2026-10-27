plugins {
    alias(libs.plugins.noveldokusha.android.library)
}

android {
    namespace = "my.noveldokusha.tooling.ai_engine"
}

dependencies {
    implementation(projects.core)

    // Networking (separate client from the scraper one)
    implementation(libs.okhttp)
    implementation(libs.okhttp.interceptor.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.test.junit)
}
