plugins {
    id("kdiab.kotlin-base")
    `maven-publish`
}

group = "org.javafreedom.kdiab"
// Version is set by the CI publish workflow via -PpublishVersion=<semver tag>.
// Falls back to "0.0.0-SNAPSHOT" for local builds where publishing is not intended.
version = (project.findProperty("publishVersion") as String?) ?: "0.0.0-SNAPSHOT"

dependencies {
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.autoconfigure)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.default.headers)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/triplem/kdiab")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["java"])
            pom {
                name.set("kdiab-common")
                description.set("Shared Ktor plugins, domain types, and utilities for kdiab services")
                url.set("https://github.com/triplem/kdiab")
            }
        }
    }
}
