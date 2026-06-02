import org.gradle.api.attributes.Category
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("kdiab.ktor-service")
}

group = "org.javafreedom.kdiab.analyze"
version = "0.1.0"

val measuresSpec by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec")) }
}
val treatmentsSpec by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec")) }
}
val profilesSpec by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec")) }
}

dependencies {
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    measuresSpec("org.javafreedom.kdiab:kdiab-measures-spec")
    treatmentsSpec("org.javafreedom.kdiab:kdiab-treatments-spec")
    profilesSpec("org.javafreedom.kdiab:kdiab-profiles-spec")
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-measures/src/main/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-treatments/src/main/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-profiles/src/main/kotlin"))
        }
    }
}

val generateMeasuresModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(provider { measuresSpec.singleFile.absolutePath })
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-measures")
    packageName.set("org.javafreedom.kdiab.analyze.api.upstream.measures")
    modelPackage.set("org.javafreedom.kdiab.analyze.api.upstream.measures.models")
    apiPackage.set("org.javafreedom.kdiab.analyze.api.upstream.measures")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    schemaMappings.set(mapOf("MeasurePayload" to "kotlinx.serialization.json.JsonObject"))
    importMappings.set(mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
}

val generateTreatmentsModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(provider { treatmentsSpec.singleFile.absolutePath })
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-treatments")
    packageName.set("org.javafreedom.kdiab.analyze.api.upstream.treatments")
    modelPackage.set("org.javafreedom.kdiab.analyze.api.upstream.treatments.models")
    apiPackage.set("org.javafreedom.kdiab.analyze.api.upstream.treatments")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    schemaMappings.set(mapOf("TreatmentPayload" to "kotlinx.serialization.json.JsonObject"))
    importMappings.set(mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject"))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
}

val generateProfilesModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(provider { profilesSpec.singleFile.absolutePath })
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-profiles")
    packageName.set("org.javafreedom.kdiab.analyze.api.upstream.profiles")
    modelPackage.set("org.javafreedom.kdiab.analyze.api.upstream.profiles.models")
    apiPackage.set("org.javafreedom.kdiab.analyze.api.upstream.profiles")
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
    dependsOn(generateMeasuresModels, generateTreatmentsModels, generateProfilesModels)
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
