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
                    "org.javafreedom.kdiab.carbs.infrastructure.persistence.ExposedFoodEntryRepository*",
                    "org.javafreedom.kdiab.carbs.infrastructure.persistence.FoodEntriesTable*",
                    "org.javafreedom.kdiab.carbs.adapters.inbound.web.FoodEntryRoutesKt*",
                    "org.javafreedom.kdiab.carbs.adapters.inbound.web.PagedFoodResponseDto*",
                    "org.javafreedom.kdiab.carbs.plugins.StatusPagesKt*",
                    "org.javafreedom.kdiab.carbs.plugins.SecurityKt*",
                    "org.javafreedom.kdiab.carbs.plugins.UserPrincipal*",
                    "org.javafreedom.kdiab.carbs.plugins.ErrorResponse*",
                    "org.javafreedom.kdiab.carbs.domain.exception.*",
                    "org.javafreedom.kdiab.carbs.domain.model.Role*"
                )
                packages("org.javafreedom.kdiab.carbs.api")
            }
        }
        verify {
            rule { bound { minValue = 80 } }
        }
    }
}
