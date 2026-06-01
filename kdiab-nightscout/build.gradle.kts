import org.gradle.api.attributes.Category
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("kdiab.ktor-service")
}

group = "org.javafreedom.kdiab.nightscout"
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
val carbsSpec by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec")) }
}
val profilesSpec by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec")) }
}
val usersSpec by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec")) }
}

dependencies {
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    measuresSpec("org.javafreedom.kdiab:kdiab-measures-spec")
    treatmentsSpec("org.javafreedom.kdiab:kdiab-treatments-spec")
    carbsSpec("org.javafreedom.kdiab:kdiab-carbs-spec")
    profilesSpec("org.javafreedom.kdiab:kdiab-profiles-spec")
    usersSpec("org.javafreedom.kdiab:kdiab-users-spec")
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-measures/src/main/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-treatments/src/main/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-carbs/src/main/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-profiles/src/main/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/upstream-users/src/main/kotlin"))
        }
    }
}

val generateMeasuresModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(provider { measuresSpec.singleFile.absolutePath })
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-measures")
    packageName.set("org.javafreedom.kdiab.nightscout.api.upstream.measures")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.measures.models")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.measures")
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
    packageName.set("org.javafreedom.kdiab.nightscout.api.upstream.treatments")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.treatments.models")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.treatments")
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

val generateCarbsModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(provider { carbsSpec.singleFile.absolutePath })
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-carbs")
    packageName.set("org.javafreedom.kdiab.nightscout.api.upstream.carbs")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.carbs.models")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.carbs")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
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
    packageName.set("org.javafreedom.kdiab.nightscout.api.upstream.profiles")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.profiles.models")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.profiles")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String"))
}

val generateUsersModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(provider { usersSpec.singleFile.absolutePath })
    outputDir.set("${layout.buildDirectory.get()}/generated/upstream-users")
    packageName.set("org.javafreedom.kdiab.nightscout.api.upstream.users")
    modelPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.users.models")
    apiPackage.set("org.javafreedom.kdiab.nightscout.api.upstream.users")
    globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
    configOptions.set(mapOf(
        "library" to "jvm-ktor",
        "dateLibrary" to "string",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
    ))
    typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String", "date" to "kotlin.String"))
}

tasks.compileKotlin {
    dependsOn(generateMeasuresModels, generateTreatmentsModels, generateCarbsModels,
        generateProfilesModels, generateUsersModels)
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
                    "org.javafreedom.kdiab.nightscout.plugins",
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
