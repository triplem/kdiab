import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("kdiab.ktor-service")
}

group = "org.javafreedom.kdiab.calc"
version = "0.1.0"

dependencies {
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-profiles/src/main/kotlin"))
        }
    }
}

val generateProfilesModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("../kdiab-profiles/api/openapi.yaml").asFile.absolutePath)
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-profiles")
    packageName.set("org.javafreedom.kdiab.calc.api.upstream.profiles")
    modelPackage.set("org.javafreedom.kdiab.calc.api.upstream.profiles.models")
    apiPackage.set("org.javafreedom.kdiab.calc.api.upstream.profiles")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
}

tasks.compileKotlin {
    dependsOn(generateProfilesModels)
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
                    "org.javafreedom.kdiab.calc.api.upstream.profiles.models",
                    "org.javafreedom.kdiab.calc.adapters.inbound.web",
                    "org.javafreedom.kdiab.calc.adapters.outbound.http",
                    "org.javafreedom.kdiab.calc.plugins",
                    "org.javafreedom.kdiab.calc.domain.exception"
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
