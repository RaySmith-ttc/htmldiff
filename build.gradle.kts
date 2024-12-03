plugins {
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.publish)
}

group = "ru.raysmith"
version = "1.0.0"

dependencies {
    implementation(libs.jsoup)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.jvm)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(17)
}