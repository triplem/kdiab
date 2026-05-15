plugins {
    alias(libs.plugins.asciidoctor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.cyclonedx)
    application
}

group = "org.javafreedom.kdiab.users"
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

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.liquibase.core)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.h2)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
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
                implementation(libs.h2)
                implementation(libs.liquibase.core)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }

            sources {
                kotlin { setSrcDirs(listOf("src/integration-test/kotlin")) }
                resources { setSrcDirs(listOf("src/integration-test/resources")) }
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
    mainClass.set("org.javafreedom.kdiab.users.ApplicationKt")
}

kover {
    reports {
        filters {
            excludes {
                classes("org.javafreedom.kdiab.users.ApplicationKt*")
                packages(
                    "org.javafreedom.kdiab.users.adapters.inbound.web",
                    "org.javafreedom.kdiab.users.infrastructure.persistence",
                    "org.javafreedom.kdiab.users.infrastructure.keycloak",
                    "org.javafreedom.kdiab.users.plugins",
                    "org.javafreedom.kdiab.users.domain.exception",
                )
            }
        }
        verify {
            rule {
                bound { minValue = 80 }
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
    attributes(mapOf("toc" to "left", "icons" to "font", "source-highlighter" to "rouge"))
}

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
