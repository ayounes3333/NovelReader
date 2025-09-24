package my.noveldoksuha.convention.plugin

import org.gradle.api.JavaVersion

internal object appConfig {
    val javaVersion = JavaVersion.VERSION_17
    const val JAVA_VERSION_STRING = "17"
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 34  // Keep target SDK at 34 for compatibility
    const val MIN_SDK = 26
}