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

group = "org.javafreedom.kdiab.nightscout"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.javafreedom.kdiab:kdiab-common")

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
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

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(libs.logback.json.classic)
    implementation(libs.logback.jackson)

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
            kotlin.srcDir("${layout.buildDirectory.get()}/generated/upstream-measures/src/main/kotlin")
            kotlin.srcDir("${layout.buildDirectory.get()}/generated/upstream-treatments/src/main/kotlin")
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
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
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

application {
    mainClass.set("org.javafreedom.kdiab.nightscout.ApplicationKt")
}

val generateMeasuresModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("../kdiab-measures/api/openapi.yaml").asFile.absolutePath)
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-measures")
    packageName.set("org.javafreedom.kdiab.nightscout.api.upstream.measures")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.measures.models")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.measures")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    schemaMappings.set(mapOf(
        "MeasurePayload" to "kotlinx.serialization.json.JsonObject",
    ))
    importMappings.set(mapOf(
        "JsonObject" to "kotlinx.serialization.json.JsonObject",
    ))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
}

val generateTreatmentsModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("../kdiab-treatments/api/openapi.yaml").asFile.absolutePath)
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-treatments")
    packageName.set("org.javafreedom.kdiab.nightscout.api.upstream.treatments")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.treatments.models")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.treatments")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    schemaMappings.set(mapOf(
        "TreatmentPayload" to "kotlinx.serialization.json.JsonObject",
    ))
    importMappings.set(mapOf(
        "JsonObject" to "kotlinx.serialization.json.JsonObject",
    ))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
}

tasks.compileKotlin {
    dependsOn(generateMeasuresModels, generateTreatmentsModels)
}

kover {
    // Both 'test' and 'integrationTest' tasks are instrumented by default in Kover 0.9. (#599)
    // No tasks are disabled here so integration-test coverage contributes to the aggregate.
    reports {
        filters {
            excludes {
                classes(
                    // Entry point -- no logic to measure
                    "org.javafreedom.kdiab.nightscout.ApplicationKt*"
                )
                packages(
                    // Generated upstream client models -- not hand-written, excluded by convention
                    "org.javafreedom.kdiab.nightscout.api.upstream.measures",
                    "org.javafreedom.kdiab.nightscout.api.upstream.treatments",
                    // Adapters require live Ktor test engine or running upstream services;
                    // covered by integration tests, not unit tests (#599)
                    "org.javafreedom.kdiab.nightscout.adapters.inbound.web",
                    "org.javafreedom.kdiab.nightscout.adapters.outbound.http",
                    // Ktor plugins require a running server to test
                    "org.javafreedom.kdiab.nightscout.plugins",
                    // Exception data classes have no logic to measure
                    "org.javafreedom.kdiab.nightscout.domain.exception"
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

tasks.register("buildAll") {
    group = "build"
    description = "Alias for 'build' -- used by root composite aggregate task."
    dependsOn(tasks.named("build"))
}

tasks.register("checkAll") {
    group = "verification"
    description = "Alias for 'check' -- used by root composite aggregate task."
    dependsOn(tasks.named("check"))
}

tasks.register("cleanAll") {
    group = "build"
    description = "Alias for 'clean' -- used by root composite aggregate task."
    dependsOn(tasks.named("clean"))
}
