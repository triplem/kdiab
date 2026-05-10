import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.asciidoctor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.cyclonedx)
    application
}

group = "org.javafreedom.kdiab.calc"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.hsts)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.status.pages)

    // Ktor Client (for calling upstream kdiab-profiles)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Serialization & utilities
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    // Tests
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
    sourceSets {
        main {
            kotlin.srcDir("src/main/kotlin")
            kotlin.srcDir("${layout.buildDirectory.get()}/generated/api/src/main/kotlin")
            kotlin.srcDir("${layout.buildDirectory.get()}/generated/upstream-profiles/src/main/kotlin")
            kotlin.exclude("**/AppMain.kt")
        }
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }

        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }

            sources {
                kotlin {
                    setSrcDirs(listOf("src/integration-test/kotlin"))
                }
                resources {
                    setSrcDirs(listOf("src/integration-test/resources"))
                }
            }
        }

        val e2eTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotest.assertions.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.server.test.host)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(testing.suites.named("integrationTest"))
                    }
                }
            }

            sources {
                kotlin {
                    setSrcDirs(listOf("src/e2e-test/kotlin"))
                }
                resources {
                    setSrcDirs(listOf("src/e2e-test/resources"))
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
    dependsOn(testing.suites.named("e2eTest"))
    dependsOn("koverVerify")
}

tasks.withType<Test> {
    jvmArgs("-Djdk.attach.allowAttachSelf=true", "-XX:+EnableDynamicAgentLoading")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get())
    extendsFrom(configurations.testImplementation.get())
}

configurations.named("e2eTestImplementation") {
    extendsFrom(configurations.implementation.get())
    extendsFrom(configurations.testImplementation.get())
}

application {
    mainClass.set("org.javafreedom.kdiab.calc.ApplicationKt")
}

openApiGenerate {
    generatorName.set("kotlin-server")
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.calc.api")
    apiPackage.set("org.javafreedom.kdiab.calc.api")
    modelPackage.set("org.javafreedom.kdiab.calc.api.models")
    typeMappings.set(mapOf(
        "UUID" to "kotlin.String",
        "date-time" to "kotlin.String"
    ))
    globalProperties.set(mapOf(
        "models" to "",
        "apis" to "",
        "supportingFiles" to ""
    ))
    configOptions.set(mapOf(
        "library" to "ktor",
        "dateLibrary" to "java8",
        "serializationLibrary" to "kotlinx_serialization"
    ))
    templateDir.set(layout.projectDirectory.dir("openapi-templates").asFile.path)
}

val generateProfilesModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin-server")
    inputSpec.set(layout.projectDirectory.file("../kdiab-profiles/api/openapi.yaml").asFile.absolutePath)
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-profiles")
    packageName.set("org.javafreedom.kdiab.calc.api.upstream.profiles")
    modelPackage.set("org.javafreedom.kdiab.calc.api.upstream.profiles.models")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    configOptions.set(mapOf(
        "library" to "ktor",
        "dateLibrary" to "java8",
        "serializationLibrary" to "kotlinx_serialization",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
    templateDir.set(layout.projectDirectory.dir("openapi-templates").asFile.path)
}

tasks.compileKotlin {
    dependsOn(tasks.named("openApiGenerate"), generateProfilesModels)
}

tasks.named<ProcessResources>("processResources") {
    from(layout.projectDirectory.file("api/openapi.yaml")) {
        into(".")
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "org.javafreedom.kdiab.calc.ApplicationKt*"
                )
                packages(
                    "org.javafreedom.kdiab.calc.api",
                    "org.javafreedom.kdiab.calc.api.upstream.profiles.models",
                    // Adapters require live Ktor test engine or running upstream services;
                    // covered by integration/e2e tests, not unit tests
                    "org.javafreedom.kdiab.calc.adapters.inbound.web",
                    "org.javafreedom.kdiab.calc.adapters.outbound.http",
                    // Ktor plugins require a running server to test
                    "org.javafreedom.kdiab.calc.plugins",
                    // Exception data classes have no logic to measure
                    "org.javafreedom.kdiab.calc.domain.exception"
                )
            }
        }

        verify {
            rule {
                bound {
                    minValue = 80
                }
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom(files("src/main/kotlin"))
}

tasks.named<io.gitlab.arturbosch.detekt.Detekt>("detektMain") {
    source = objects.fileCollection().from("src/main/kotlin").asFileTree
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("bin"))
}

// Docs generation
tasks.asciidoctor {
    baseDirFollowsSourceFile()
    sourceDir(file("docs"))
    setOutputDir(file("build/docs/asciidoc"))
    attributes(
        mapOf(
            "toc" to "left",
            "icons" to "font",
            "source-highlighter" to "rouge"
        )
    )
}

// ── Composite build aliases (used by root aggregate tasks) ────────────────────
tasks.register("buildAll") {
    group = "build"
    description = "Alias for 'build' — used by root composite aggregate task."
    dependsOn(tasks.named("build"))
}

tasks.register("checkAll") {
    group = "verification"
    description = "Alias for 'check' — used by root composite aggregate task."
    dependsOn(tasks.named("check"))
}

tasks.register("cleanAll") {
    group = "build"
    description = "Alias for 'clean' — used by root composite aggregate task."
    dependsOn(tasks.named("clean"))
}
