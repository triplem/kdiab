// ── Frontend (kdiab-ui npm) ────────────────────────────────────────────────────
val buildFrontend by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the kdiab-ui React frontend (npm ci + npm run build)."
    workingDir(layout.projectDirectory.dir("kdiab-ui"))
    commandLine("bash", "-c", "npm ci --silent && npm run build")
}

// ── Backend aggregate (no frontend) ───────────────────────────────────────────
val buildBackends by tasks.registering {
    group = "build"
    description = "Builds all five service backends via Gradle (no frontend)."
    dependsOn(
        gradle.includedBuild("kdiab-measures").task(":buildAll"),
        gradle.includedBuild("kdiab-profiles").task(":buildAll"),
        gradle.includedBuild("kdiab-treatments").task(":buildAll"),
        gradle.includedBuild("kdiab-analyze").task(":buildAll"),
        gradle.includedBuild("kdiab-carbs").task(":buildAll"),
    )
}

// ── Full build (backends + frontend) ──────────────────────────────────────────
tasks.register("build") {
    group = "build"
    description = "Builds all service backends and the kdiab-ui frontend."
    dependsOn(buildBackends, buildFrontend)
}

// ── Full check (all tests, detekt, kover) ─────────────────────────────────────
tasks.register("check") {
    group = "verification"
    description = "Runs all tests, detekt, and kover for all service backends."
    dependsOn(
        gradle.includedBuild("kdiab-measures").task(":checkAll"),
        gradle.includedBuild("kdiab-profiles").task(":checkAll"),
        gradle.includedBuild("kdiab-treatments").task(":checkAll"),
        gradle.includedBuild("kdiab-analyze").task(":checkAll"),
        gradle.includedBuild("kdiab-carbs").task(":checkAll"),
    )
}

// ── Clean ─────────────────────────────────────────────────────────────────────
tasks.register("clean") {
    group = "build"
    description = "Deletes build outputs for all service backends."
    dependsOn(
        gradle.includedBuild("kdiab-measures").task(":cleanAll"),
        gradle.includedBuild("kdiab-profiles").task(":cleanAll"),
        gradle.includedBuild("kdiab-treatments").task(":cleanAll"),
        gradle.includedBuild("kdiab-analyze").task(":cleanAll"),
        gradle.includedBuild("kdiab-carbs").task(":cleanAll"),
    )
}

// ── Docker ────────────────────────────────────────────────────────────────────
tasks.register<Exec>("dockerBuild") {
    group = "podman"
    description = "Builds all kdiab Docker images via docker compose. JARs are pre-built by buildBackends."
    dependsOn(buildBackends)
    workingDir(layout.projectDirectory)
    commandLine(
        "bash", "-c",
        "podman compose build liquibase-measures liquibase-profiles " +
        "liquibase-treatments liquibase-carbs measures-backend profiles-backend treatments-backend " +
        "carbs-backend analyze-backend kdiab-ui"
    )
}

tasks.register<Exec>("dockerClean") {
    group = "podman"
    description = "Stops containers, removes kdiab images and volumes (DB reset)."
    workingDir(layout.projectDirectory)
    commandLine(
        "bash", "-c",
        "podman compose down -v --remove-orphans; " +
        "podman images --filter 'reference=localhost/kdiab-*' -q | xargs -r docker rmi; " +
        "podman builder prune -f"
    )
}
