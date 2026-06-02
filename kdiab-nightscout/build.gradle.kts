plugins {
    id("kdiab.ktor-service")
}

group = "org.javafreedom.kdiab.nightscout"
version = "0.1.0"

registerUpstreamSpec("measures", "org.javafreedom.kdiab:kdiab-measures-spec",
    schemaMappings = mapOf("MeasurePayload" to "kotlinx.serialization.json.JsonObject"),
    importMappings = mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))

registerUpstreamSpec("treatments", "org.javafreedom.kdiab:kdiab-treatments-spec",
    schemaMappings = mapOf("TreatmentPayload" to "kotlinx.serialization.json.JsonObject"),
    importMappings = mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))

registerUpstreamSpec("carbs", "org.javafreedom.kdiab:kdiab-carbs-spec")

registerUpstreamSpec("profiles", "org.javafreedom.kdiab:kdiab-profiles-spec")

registerUpstreamSpec("users", "org.javafreedom.kdiab:kdiab-users-spec",
    extraTypeMappings = mapOf("date" to "kotlin.String"))

dependencies {
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.nightscout.api")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.models")
}

application {
    mainClass.set("org.javafreedom.kdiab.nightscout.ApplicationKt")
}

kover {
    reports {
        filters {
            excludes {
                classes("org.javafreedom.kdiab.nightscout.ApplicationKt*")
                packages(
                    "org.javafreedom.kdiab.nightscout.api",
                    "org.javafreedom.kdiab.nightscout.api.upstream.measures",
                    "org.javafreedom.kdiab.nightscout.api.upstream.treatments",
                    "org.javafreedom.kdiab.nightscout.api.upstream.carbs",
                    "org.javafreedom.kdiab.nightscout.api.upstream.profiles",
                    "org.javafreedom.kdiab.nightscout.api.upstream.users",
                    "org.javafreedom.kdiab.nightscout.adapters.inbound.web",
                    "org.javafreedom.kdiab.nightscout.adapters.outbound.http",
                    "org.javafreedom.kdiab.nightscout.domain.exception"
                )
                classes(
                    "org.javafreedom.kdiab.nightscout.domain.model.Ns3*",
                    "org.javafreedom.kdiab.nightscout.domain.model.NightscoutV3ModelsKt",
                    "org.javafreedom.kdiab.nightscout.domain.model.NightscoutStatus*"
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
