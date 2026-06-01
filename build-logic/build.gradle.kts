plugins {
    `kotlin-dsl`
}

private val catalog get() = the<VersionCatalogsExtension>().named("libs")
private fun VersionCatalog.v(alias: String) = findVersion(alias).get().requiredVersion

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${catalog.v("kotlin")}")
    implementation("org.jetbrains.kotlin:kotlin-serialization:${catalog.v("kotlin")}")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${catalog.v("detekt")}")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:${catalog.v("kover")}")
    implementation("io.ktor.plugin:io.ktor.plugin.gradle.plugin:${catalog.v("ktor")}")
    implementation("org.openapitools:openapi-generator-gradle-plugin:${catalog.v("openapi-generator")}")
    implementation("org.asciidoctor:asciidoctor-gradle-jvm:${catalog.v("asciidoctor")}")
    implementation("org.cyclonedx:cyclonedx-gradle-plugin:${catalog.v("cyclonedx")}")
}
