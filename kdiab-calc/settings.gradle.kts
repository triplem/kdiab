pluginManagement {
    includeBuild("../build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "kdiab-calc"

includeBuild("../kdiab-common")
includeBuild("../kdiab-profiles") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-profiles")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-profiles-spec")).using(project(":"))
    }
}
