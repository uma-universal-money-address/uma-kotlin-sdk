plugins {
    id("java-library")
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    dependencies {
        testImplementation("com.google.code.gson:gson:2.10.1")
        testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
        testImplementation(libs.mockito.core)
        testImplementation(libs.kotlin.serialization.json)
        implementation(project(":uma-sdk"))
    }
}

tasks.test {
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging { events("passed") }
}
