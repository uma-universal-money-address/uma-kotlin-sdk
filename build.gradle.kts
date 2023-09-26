plugins {
    kotlin("multiplatform") version "1.9.0"
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
}

group = "me.uma"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
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
                implementation(libs.acinq.secp256k1)
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
                implementation(libs.acinq.secp256k1.jni.jvm)
                implementation(libs.kotlinx.coroutines.jdk8)
                implementation(libs.ktor.client.okhttp)
            }
        }
        val jvmTest by getting
    }
}
