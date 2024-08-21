plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
    id(libs.plugins.dokka.get().pluginId)
    id(libs.plugins.mavenPublish.get().pluginId)
}

kotlin {
    jvmToolchain(11)
    jvm {
        jvmToolchain(11)
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    // Will add other platforms as needed.

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.jna)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.jdk8)
                implementation(libs.ktor.client.okhttp)
            }
        }
        val jvmTest by getting
    }
}

ktlint {
    filter {
        exclude("**/internal/UmaCrypto.kt")
    }
}
