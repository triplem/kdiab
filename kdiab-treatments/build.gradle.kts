plugins {
    id("kdiab.ktor-db-service")
}

group = "org.javafreedom.kdiab.treatments"
version = "0.1.0"

application {
    mainClass.set("org.javafreedom.kdiab.treatments.ApplicationKt")
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.treatments.api")
    apiPackage.set("org.javafreedom.kdiab.treatments.api")
    modelPackage.set("org.javafreedom.kdiab.treatments.api.models")
    typeMappings.set(mapOf(
        "UUID" to "kotlin.String",
        "date-time" to "kotlin.String",
        "number" to "kotlin.Double"
    ))
    schemaMappings.set(mapOf("TreatmentPayload" to "kotlinx.serialization.json.JsonObject"))
    importMappings.set(mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "org.javafreedom.kdiab.treatments.ApplicationKt*",
                    "org.javafreedom.kdiab.treatments.infrastructure.persistence.TreatmentsTable*",
                    "org.javafreedom.kdiab.treatments.infrastructure.persistence.ExposedDeviceStatusRepository*",
                    "org.javafreedom.kdiab.treatments.infrastructure.persistence.DeviceStatusTable*"
                )
                packages("org.javafreedom.kdiab.treatments.api")
            }
        }
        verify {
            rule { bound { minValue = 80 } }
        }
    }
}
