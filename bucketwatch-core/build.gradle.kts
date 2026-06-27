plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // Targets Java 11 bytecode per BRD §7.4 (NFR11). The toolchain is auto-provisioned
    // by the foojay resolver in settings.gradle.kts if no JDK 11 is installed locally.
    jvmToolchain(11)

    // Enforce explicit visibility on all public API — this is a published library.
    explicitApi()
}

dependencies {
    // Required runtime dependency, exposed to consumers (they pass their own S3Client).
    api(platform(libs.aws.bom))
    api(libs.aws.s3)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
