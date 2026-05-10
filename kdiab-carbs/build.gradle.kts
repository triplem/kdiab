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

group = "org.javafreedom.kdiab.carbs"
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

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    // Database (Exposed + Postgres)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time) // Date/Time support
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.mockk)
    testImplementation(libs.h2)

    // Infrastructure
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
    mainClass.set("org.javafreedom.kdiab.carbs.ApplicationKt")
}

// Generate API Classes
openApiGenerate {
    generatorName.set("kotlin-server")
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.carbs.api")
    apiPackage.set("org.javafreedom.kdiab.carbs.api")
    modelPackage.set("org.javafreedom.kdiab.carbs.api.models")
    typeMappings.set(mapOf(
        "UUID" to "kotlin.String",
        "date-time" to "kotlin.String",
        "number" to "kotlin.Double"
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

tasks.compileKotlin {
    dependsOn(tasks.named("openApiGenerate"))
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
                    "org.javafreedom.kdiab.carbs.ApplicationKt*",
                    "org.javafreedom.kdiab.carbs.infrastructure.persistence.DatabaseFactory*",
                    // DB-layer classes require a live database; covered by integration tests
                    "org.javafreedom.kdiab.carbs.infrastructure.persistence.ExposedFoodEntryRepository*",
                    "org.javafreedom.kdiab.carbs.infrastructure.persistence.FoodEntriesTable*",
                    // Route handlers require auth+DB; covered by integration/e2e tests
                    "org.javafreedom.kdiab.carbs.adapters.inbound.web.FoodEntryRoutesKt*",
                    "org.javafreedom.kdiab.carbs.adapters.inbound.web.PagedFoodResponseDto*",
                    // Plugin infrastructure — tested via integration
                    "org.javafreedom.kdiab.carbs.plugins.StatusPagesKt*",
                    "org.javafreedom.kdiab.carbs.plugins.SecurityKt*",
                    "org.javafreedom.kdiab.carbs.plugins.UserPrincipal*",
                    "org.javafreedom.kdiab.carbs.plugins.ErrorResponse*",
                    // Domain exceptions and Role are trivial value types
                    "org.javafreedom.kdiab.carbs.domain.exception.*",
                    "org.javafreedom.kdiab.carbs.domain.model.Role*"
                )
                packages(
                    "org.javafreedom.kdiab.carbs.api"
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
