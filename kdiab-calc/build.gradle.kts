plugins {
    id("kdiab.ktor-service")
}

group = "org.javafreedom.kdiab.calc"
version = "0.1.0"

registerUpstreamSpec("profiles", "org.javafreedom.kdiab:kdiab-profiles-spec")

dependencies {
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set("${layout.buildDirectory.get()}/generated/api")
    packageName.set("org.javafreedom.kdiab.calc.api")
    apiPackage.set("org.javafreedom.kdiab.calc.api")
    modelPackage.set("org.javafreedom.kdiab.calc.api.models")
}

application {
    mainClass.set("org.javafreedom.kdiab.calc.ApplicationKt")
}

kover {
    reports {
        filters {
            excludes {
                classes("org.javafreedom.kdiab.calc.ApplicationKt*")
                packages(
                    "org.javafreedom.kdiab.calc.api",
                    "org.javafreedom.kdiab.calc.api.upstream.profiles",
                    "org.javafreedom.kdiab.calc.api.upstream.profiles.models",
                    "org.javafreedom.kdiab.calc.adapters.outbound.http",
                    "org.javafreedom.kdiab.calc.plugins",
                    "org.javafreedom.kdiab.calc.domain.exception"
                )
                // CalcRoutes.kt requires a running Ktor application; tested at integration/e2e level
                classes("org.javafreedom.kdiab.calc.adapters.inbound.web.CalcRoutesKt*")
            }
        }
        verify {
            rule {
                bound { minValue = 80 }
            }
        }
    }
}
