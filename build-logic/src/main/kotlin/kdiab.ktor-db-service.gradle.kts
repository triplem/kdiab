import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("kdiab.ktor-service")
}

private fun catalog(): VersionCatalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
private fun lib(alias: String) = catalog().findLibrary(alias).get()
private fun bundle(alias: String) = catalog().findBundle(alias).get()

dependencies {
    "implementation"(bundle("database"))
    "integrationTestImplementation"(lib("h2"))
}
