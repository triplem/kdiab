plugins {
    id("kdiab.ktor-db-service")
}

group = "org.javafreedom.kdiab.users"
version = "0.1.0"

dependencies {
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}

application {
    mainClass.set("org.javafreedom.kdiab.users.ApplicationKt")
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.users.api")
    apiPackage.set("org.javafreedom.kdiab.users.api")
    modelPackage.set("org.javafreedom.kdiab.users.api.models")
    // format: date → String avoids missing LocalDate kotlinx serializer
    typeMappings.set(mapOf(
        "UUID" to "kotlin.String",
        "date-time" to "kotlin.String",
        "date" to "kotlin.String"
    ))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "org.javafreedom.kdiab.users.ApplicationKt*",
                    // Route handlers: tested via integration/e2e tests, not unit tests
                    "org.javafreedom.kdiab.users.adapters.inbound.web.ApiKeyRoutesKt*",
                    "org.javafreedom.kdiab.users.adapters.inbound.web.DoctorPatientRoutesKt*",
                    "org.javafreedom.kdiab.users.adapters.inbound.web.InvitationRoutesKt*",
                    "org.javafreedom.kdiab.users.adapters.inbound.web.UserRoutesKt*",
                )
                packages(
                    "org.javafreedom.kdiab.users.api",
                    "org.javafreedom.kdiab.users.adapters.inbound.web",
                    "org.javafreedom.kdiab.users.infrastructure.persistence",
                    "org.javafreedom.kdiab.users.infrastructure.keycloak",
                )
            }
        }
        verify {
            rule { bound { minValue = 80 } }
        }
    }
}
