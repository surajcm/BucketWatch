plugins {
    // Applied (not configured) at the root so the Kotlin Gradle plugin is on the
    // classpath for all modules; each module opts in via its own build file.
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "io.github.surajcm"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
