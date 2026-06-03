import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

fun Project.registerUpstreamSpec(
    serviceName: String,
    moduleId: String,
    schemaMappings: Map<String, String> = emptyMap(),
    importMappings: Map<String, String> = emptyMap(),
    extraTypeMappings: Map<String, String> = emptyMap(),
) {
    val specConfigName = "${serviceName}Spec"
    val specConfig = configurations.create(specConfigName) {
        isCanBeConsumed = false
        isCanBeResolved = true
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, "openapi-spec"))
        }
    }

    dependencies.add(specConfigName, moduleId)

    val serviceNameCap = serviceName.replaceFirstChar { it.titlecase() }
    val outputDirPath = "${layout.buildDirectory.get()}/generated/upstream-${serviceName}"
    val basePackage = "${project.group}.api.upstream.${serviceName}"

    extensions.configure(KotlinJvmProjectExtension::class.java) {
        sourceSets.named("main").configure {
            kotlin.srcDir("${outputDirPath}/src/main/kotlin")
        }
    }

    // Capture outer params before entering GenerateTask lambda where same names are properties
    val capturedSchemaMappings = schemaMappings
    val capturedImportMappings = importMappings

    val generateTask = tasks.register("generate${serviceNameCap}Models", GenerateTask::class.java) {
        generatorName.set("kotlin")
        inputSpec.set(provider { specConfig.singleFile.absolutePath })
        // Track spec file content so Gradle invalidates the build cache when the spec changes.
        // Without this, inputSpec is a @Input String (path only) and stale cache entries survive spec updates.
        inputs.files(specConfig)
        outputDir.set(outputDirPath)
        packageName.set(basePackage)
        modelPackage.set("${basePackage}.models")
        apiPackage.set(basePackage)
        globalProperties.set(mapOf("models" to "", "apis" to "", "supportingFiles" to ""))
        if (capturedSchemaMappings.isNotEmpty()) this.schemaMappings.set(capturedSchemaMappings)
        if (capturedImportMappings.isNotEmpty()) this.importMappings.set(capturedImportMappings)
        configOptions.set(
            mapOf(
                "library" to "jvm-ktor",
                "dateLibrary" to "string",
                "serializationLibrary" to "kotlinx_serialization",
                "useCoroutines" to "true",
            )
        )
        typeMappings.set(mapOf("UUID" to "kotlin.String", "date-time" to "kotlin.String") + extraTypeMappings)
    }

    tasks.named("compileKotlin") {
        dependsOn(generateTask)
    }
}
