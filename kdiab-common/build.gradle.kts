plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "org.javafreedom.kdiab"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.autoconfigure)
    implementation(libs.opentelemetry.exporter.otlp)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
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
