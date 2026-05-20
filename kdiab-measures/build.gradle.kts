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

group = "org.javafreedom.kdiab.measures"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Common shared library
    implementation("org.javafreedom.kdiab:kdiab-common")

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
    implementation(libs.ktor.server.di)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(libs.logback.json.classic)
    implementation(libs.logback.jackson)

    // Database (Exposed + Postgres)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time) // Date/Time support
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.exposed.json) // JSON support
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.mockk)
    testImplementation(libs.h2)
    
    // Liquibase is used in production (behind a runMigrations config flag) to run migrations
    // in test environments (H2 in-memory). Production Docker containers run Liquibase separately
    // via a dedicated container; the flag is set to false in application.conf.
    implementation(libs.liquibase.core)
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

// Ensure the check task runs integration and e2e tests
tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
    dependsOn(testing.suites.named("e2eTest"))
    dependsOn("koverVerify")
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Inherit dependencies from main and test
configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get())
    extendsFrom(configurations.testImplementation.get())
}

configurations.named("e2eTestImplementation") {
    extendsFrom(configurations.implementation.get())
    extendsFrom(configurations.testImplementation.get())
}

application {
    mainClass.set("org.javafreedom.kdiab.measures.ApplicationKt")
}

// Generate API Classes
// Common defaults live in gradle/openapi-defaults.properties (#598).
// Only service-specific values (spec path, packages, schema mappings) are declared here.
openApiGenerate {
    generatorName.set("kotlin-server")
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.measures.api")
    apiPackage.set("org.javafreedom.kdiab.measures.api")
    modelPackage.set("org.javafreedom.kdiab.measures.api.models")
    typeMappings.set(mapOf(
        "UUID" to "kotlin.String",
        "date-time" to "kotlin.String"
    ))
    schemaMappings.set(mapOf(
        "MeasurePayload" to "kotlinx.serialization.json.JsonObject"
    ))
    importMappings.set(mapOf(
        "JsonObject" to "kotlinx.serialization.json.JsonObject"
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

tasks.compileKotlin {
    dependsOn(tasks.named("openApiGenerate"))
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
                    "org.javafreedom.kdiab.measures.ApplicationKt*",
                    // DB-layer classes require a live database; covered by integration tests (#599)
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedMeasureRepository*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.MeasuresTable*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedAuditLogRepository*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.AuditLogsTable*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedHbA1cEntryRepository*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.HbA1cEntriesTable*"
                )
                packages(
                    // Generated OpenAPI stubs -- not hand-written, excluded by convention
                    "org.javafreedom.kdiab.measures.api"
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
    buildUponDefaultConfig = true // preconfigure defaults
    allRules = false // activate all available (even unstable) rules.
    config.setFrom(files("$rootDir/config/detekt/detekt.yml")) // point to your custom config defining rules to run, overwriting default behavior
    baseline = file("$rootDir/config/detekt/baseline.xml") // a way of suppressing issues before introducing detekt
    source.setFrom(files("src/main/kotlin"))
}

tasks.named<io.gitlab.arturbosch.detekt.Detekt>("detektMain") {
    source = objects.fileCollection().from("src/main/kotlin").asFileTree
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true) // observe findings in your browser with structure and code snippets
        xml.required.set(true) // checkstyle like format mainly for integrations like Jenkins
        txt.required.set(true) // similar to the console output, contains issue signature to manually edit baseline files
        sarif.required.set(true) // standardized SARIF format (supported by GitHub Code Scanning)
        md.required.set(true) // simple Markdown format
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

// Using default CycloneDX configuration for now

