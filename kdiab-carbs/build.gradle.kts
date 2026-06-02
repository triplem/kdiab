plugins {
    id("kdiab.ktor-db-service")
}

group = "org.javafreedom.kdiab.carbs"
version = "0.1.0"

application {
    mainClass.set("org.javafreedom.kdiab.carbs.ApplicationKt")
}

openApiGenerate {
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
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "org.javafreedom.kdiab.carbs.ApplicationKt*",
                    "org.javafreedom.kdiab.carbs.infrastructure.persistence.DatabaseFactory*",
                )
                packages("org.javafreedom.kdiab.carbs.api")
            }
        }
        verify {
            rule { bound { minValue = 80 } }
        }
    }
}
