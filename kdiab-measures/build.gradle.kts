plugins {
    id("kdiab.ktor-db-service")
}

group = "org.javafreedom.kdiab.measures"
version = "0.0.1"

application {
    mainClass.set("org.javafreedom.kdiab.measures.ApplicationKt")
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.measures.api")
    apiPackage.set("org.javafreedom.kdiab.measures.api")
    modelPackage.set("org.javafreedom.kdiab.measures.api.models")
    schemaMappings.set(mapOf("MeasurePayload" to "kotlinx.serialization.json.JsonObject"))
    importMappings.set(mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "org.javafreedom.kdiab.measures.ApplicationKt*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedMeasureRepository*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.MeasuresTable*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedAuditLogRepository*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.AuditLogsTable*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedHbA1cEntryRepository*",
                    "org.javafreedom.kdiab.measures.infrastructure.persistence.HbA1cEntriesTable*"
                )
                packages("org.javafreedom.kdiab.measures.api")
            }
        }
        verify {
            rule { bound { minValue = 80 } }
        }
    }
}
