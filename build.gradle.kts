import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.net.URL

buildscript {
    dependencies {
        classpath(libs.gradleClasspath.dokka)
        classpath(libs.gradleClasspath.ktlint)
        classpath(libs.gradleClasspath.kotlin)
        classpath(libs.gradleClasspath.mavenPublish)
        classpath(libs.task.tree)
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

apply(plugin = "com.dorongold.task-tree")
apply(plugin = "org.jetbrains.dokka")
apply(plugin = "org.jlleitschuh.gradle.ktlint")

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        outputToConsole.set(true)
        verbose.set(true)
        disabledRules.set(listOf("no-wildcard-imports"))
    }

    tasks.create<Exec>("bumpAndTagVersion") {
        group = "release"
        description = "Tags the current version in git."
        val cmd = mutableListOf("../scripts/versions.main.kts", "-f", "-t")
        if (project.hasProperty("newVersion")) {
            cmd.add(project.properties["newVersion"].toString())
        }
        commandLine(*cmd.toTypedArray())
    }

    tasks.create<Exec>("bumpVersion") {
        group = "release"
        description = "Tags the current version in git."
        val cmd = mutableListOf("../scripts/versions.main.kts", "-f")
        if (project.hasProperty("newVersion")) {
            cmd.addAll(listOf("-v", project.properties["newVersion"].toString()))
        }
        commandLine(*cmd.toTypedArray())
    }

    tasks.withType<DokkaTaskPartial>().configureEach {
        dokkaSourceSets.configureEach {
            reportUndocumented.set(false)
            skipDeprecated.set(true)
            jdkVersion.set(11)
            if (project.file("README.md").exists()) {
                includes.from(project.file("README.md"))
            }
            externalDocumentationLink {
                // TODO: Update this link when API Reference docs are hosted publicly.
                url.set(URL("https://app.lightspark.com/docs/reference/kotlin"))
                packageListUrl.set(URL("https://app.lightspark.com/docs/reference/kotlin/package-list"))
            }
        }
    }

    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.S01, automaticRelease = true)
            signAllPublications()
            pom {
                name.set(project.name)
                packaging = "aar"
                url.set("https://github.com/uma-universal-money-address/uma-kotlin-sdk")
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/uma-universal-money-address/uma-kotlin-sdk.git")
                    developerConnection.set("scm:git:ssh://git@github.com/uma-universal-money-address/uma-kotlin-sdk.git")
                    url.set("https://github.com/uma-universal-money-address/uma-kotlin-sdk")
                }
                developers {
                    developer {
                        name.set("Lightspark Group, Inc.")
                        id.set("uma-universal-money-address")
                        url.set("https://github.com/uma-universal-money-address")
                    }
                }
            }
        }
    }
}

tasks.named<DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
    moduleName.set("UMA Kotlin+Java SDKs")
    pluginsMapConfiguration.set(
        mapOf(
            "org.jetbrains.dokka.base.DokkaBase" to """
          {
            "customStyleSheets": [
              "${rootDir.resolve("docs/css/logo-styles.css")}"
            ],
            "customAssets" : [
              "${rootDir.resolve("docs/images/uma-logo-white.svg")}"
            ]
          }
            """.trimIndent(),
        ),
    )
}