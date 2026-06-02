plugins {
    id("kdiab.ktor-db-service")
}

group = "org.javafreedom.kdiab.profiles"
version = "0.1.0"

application {
    mainClass.set("org.javafreedom.kdiab.profiles.ApplicationKt")
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.profiles.api")
    apiPackage.set("org.javafreedom.kdiab.profiles.api")
    modelPackage.set("org.javafreedom.kdiab.profiles.api.models")
}

kover {
    reports {
        filters {
            excludes {
                classes("org.javafreedom.kdiab.profiles.ApplicationKt*")
                packages("org.javafreedom.kdiab.profiles.api")
            }
        }
        verify {
            rule { bound { minValue = 80 } }
        }
    }
}
