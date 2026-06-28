plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    group = "io.github.surajcm"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
