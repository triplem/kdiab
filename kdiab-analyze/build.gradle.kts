plugins {
    id("kdiab.ktor-service")
}

group = "org.javafreedom.kdiab.analyze"
version = "0.1.0"

registerUpstreamSpec("measures", "org.javafreedom.kdiab:kdiab-measures-spec",
    schemaMappings = mapOf("MeasurePayload" to "kotlinx.serialization.json.JsonObject"),
    importMappings = mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))

registerUpstreamSpec("treatments", "org.javafreedom.kdiab:kdiab-treatments-spec",
    schemaMappings = mapOf("TreatmentPayload" to "kotlinx.serialization.json.JsonObject"),
    importMappings = mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))

registerUpstreamSpec("profiles", "org.javafreedom.kdiab:kdiab-profiles-spec")

dependencies {
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}

tasks.compileKotlin {
    compilerOptions {
        suppressWarnings.set(true)
    }
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.analyze.api")
    apiPackage.set("org.javafreedom.kdiab.analyze.api")
    modelPackage.set("org.javafreedom.kdiab.analyze.api.models")
    schemaMappings.set(mapOf(
        "TimelineMeasureData" to "kotlinx.serialization.json.JsonObject",
        "TimelineTreatmentData" to "kotlinx.serialization.json.JsonObject"
    ))
    importMappings.set(mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))
}

application {
    mainClass.set("org.javafreedom.kdiab.analyze.ApplicationKt")
}

kover {
    reports {
        filters {
            excludes {
                classes("org.javafreedom.kdiab.analyze.ApplicationKt*")
                packages(
                    "org.javafreedom.kdiab.analyze.api",
                    "org.javafreedom.kdiab.analyze.api.upstream.measures.models",
                    "org.javafreedom.kdiab.analyze.api.upstream.treatments.models",
                    "org.javafreedom.kdiab.analyze.api.upstream.profiles.models",
                    "org.javafreedom.kdiab.analyze.adapters.outbound.http",
                    "org.javafreedom.kdiab.analyze.plugins",
                    "org.javafreedom.kdiab.analyze.domain.exception"
                )
                classes(
                    "org.javafreedom.kdiab.analyze.adapters.inbound.web.AnalyzeRoutesKt*"
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
