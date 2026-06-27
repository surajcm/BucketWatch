plugins {
    // Lets the Java toolchain (11) auto-provision a JDK when one isn't installed locally,
    // so the build produces Java 11 bytecode regardless of the contributor's default JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "bucketwatch"

include("bucketwatch-core")
