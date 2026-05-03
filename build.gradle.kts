tasks.register("build") {
    group = "build"
    description = "Builds all service backends and frontends."
    dependsOn(
        gradle.includedBuild("kdiab-measures").task(":buildAll"),
        gradle.includedBuild("kdiab-profiles").task(":buildAll"),
        gradle.includedBuild("kdiab-treatments").task(":buildAll"),
        gradle.includedBuild("kdiab-analyze").task(":buildAll"),
    )
}

tasks.register("check") {
    group = "verification"
    description = "Runs all tests, detekt, kover, and frontend lint/test for all services."
    dependsOn(
        gradle.includedBuild("kdiab-measures").task(":checkAll"),
        gradle.includedBuild("kdiab-profiles").task(":checkAll"),
        gradle.includedBuild("kdiab-treatments").task(":checkAll"),
        gradle.includedBuild("kdiab-analyze").task(":checkAll"),
    )
}

tasks.register("clean") {
    group = "build"
    description = "Deletes build outputs for all services."
    dependsOn(
        gradle.includedBuild("kdiab-measures").task(":cleanAll"),
        gradle.includedBuild("kdiab-profiles").task(":cleanAll"),
        gradle.includedBuild("kdiab-treatments").task(":cleanAll"),
        gradle.includedBuild("kdiab-analyze").task(":cleanAll"),
    )
}
