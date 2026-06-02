pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "kdiab"

includeBuild("kdiab-common")
includeBuild("kdiab-measures") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-measures")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-measures-spec")).using(project(":"))
    }
}
includeBuild("kdiab-profiles") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-profiles")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-profiles-spec")).using(project(":"))
    }
}
includeBuild("kdiab-treatments") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-treatments")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-treatments-spec")).using(project(":"))
    }
}
includeBuild("kdiab-analyze") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-analyze")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-analyze-spec")).using(project(":"))
    }
}
includeBuild("kdiab-carbs") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-carbs")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-carbs-spec")).using(project(":"))
    }
}
includeBuild("kdiab-calc") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-calc")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-calc-spec")).using(project(":"))
    }
}
includeBuild("kdiab-nightscout") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-nightscout")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-nightscout-spec")).using(project(":"))
    }
}
includeBuild("kdiab-users") {
    dependencySubstitution {
        substitute(module("org.javafreedom.kdiab:kdiab-users")).using(project(":"))
        substitute(module("org.javafreedom.kdiab:kdiab-users-spec")).using(project(":"))
    }
}
