plugins {
    kotlin("jvm") version "2.0.0"
}

group = "ru.raysmith"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-jvm:4.0.7")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}