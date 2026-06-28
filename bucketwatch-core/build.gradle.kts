plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    jacoco
}

kotlin {
    jvmToolchain(11)
    explicitApi()
}

detekt {
    source.setFrom("src/main/kotlin")
    config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    api(platform(libs.aws.bom))
    api(libs.aws.s3)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
