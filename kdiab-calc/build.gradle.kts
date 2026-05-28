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
    // Common shared library
    implementation("org.javafreedom.kdiab:kdiab-common")

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
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
    implementation(libs.ktor.server.di)

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
    implementation(libs.logback.json.classic)
    implementation(libs.logback.jackson)

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

// Common defaults live in gradle/openapi-defaults.properties (#598).
// Only service-specific values (spec path, packages, schema mappings) are declared here.
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
    // Shared template dir -- all services use the same mustache overrides (#603)
    templateDir.set(rootDir.parentFile.resolve("config/openapi-templates").path)
}

val generateProfilesModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("../kdiab-profiles/api/openapi.yaml").asFile.absolutePath)
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-profiles")
    packageName.set("org.javafreedom.kdiab.calc.api.upstream.profiles")
    modelPackage.set("org.javafreedom.kdiab.calc.api.upstream.profiles.models")
    apiPackage.set("org.javafreedom.kdiab.calc.api.upstream.profiles")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
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
    // Both 'test' and 'integrationTest' tasks are instrumented by default in Kover 0.9. (#599)
    // No tasks are disabled here so integration-test coverage contributes to the aggregate.
    reports {
        filters {
            excludes {
                classes(
                    // Entry point -- no logic to measure
                    "org.javafreedom.kdiab.calc.ApplicationKt*"
                )
                packages(
                    // Generated OpenAPI stubs -- not hand-written, excluded by convention
                    "org.javafreedom.kdiab.calc.api",
                    // Generated upstream client models -- not hand-written, excluded by convention
                    "org.javafreedom.kdiab.calc.api.upstream.profiles.models",
                    // Adapters require live Ktor test engine or running upstream services;
                    // covered by integration/e2e tests, not unit tests (#599)
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
            "source-highlighter" to "rouge",
            "revnumber" to project.version.toString()
        )
    )
}

