pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "kdiab"

includeBuild("kdiab-common")
includeBuild("kdiab-measures")
includeBuild("kdiab-profiles")
includeBuild("kdiab-treatments")
includeBuild("kdiab-analyze")
includeBuild("kdiab-carbs")
includeBuild("kdiab-calc")
includeBuild("kdiab-nightscout")
includeBuild("kdiab-users")
