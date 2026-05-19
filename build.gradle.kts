// -- Service list --------------------------------------------------------------
// The eight backend services that produce runnable JARs and Docker images.
// kdiab-common is a shared library included automatically as a substituted
// dependency -- it does not need its own build/check/clean invocation here.
// (#600) Use a single list so adding/removing a service updates all tasks at once.
val serviceBuilds = listOf(
    "kdiab-measures",
    "kdiab-profiles",
    "kdiab-treatments",
    "kdiab-analyze",
    "kdiab-carbs",
    "kdiab-calc",
    "kdiab-nightscout",
    "kdiab-users",
)

// -- Frontend (kdiab-ui npm) ----------------------------------------------------
val buildFrontend by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the kdiab-ui React frontend (npm ci + npm run build)."
    workingDir(layout.projectDirectory.dir("kdiab-ui"))
    commandLine("bash", "-c", "npm ci --silent && npm run build")
}

// -- Backend aggregate (no frontend) -------------------------------------------
val buildBackends by tasks.registering {
    group = "build"
    description = "Builds all eight service backends via Gradle (no frontend)."
    dependsOn(serviceBuilds.map { gradle.includedBuild(it).task(":build") })
}

// -- Full build (backends + frontend) ------------------------------------------
tasks.register("build") {
    group = "build"
    description = "Builds all service backends and the kdiab-ui frontend."
    dependsOn(buildBackends, buildFrontend)
}

// -- Full check (all tests, detekt, kover) -------------------------------------
tasks.register("check") {
    group = "verification"
    description = "Runs all tests, detekt, and kover for all service backends."
    dependsOn(serviceBuilds.map { gradle.includedBuild(it).task(":check") })
}

// -- Clean ---------------------------------------------------------------------
val cleanFrontend by tasks.registering(Exec::class) {
    group = "build"
    description = "Deletes kdiab-ui Vite build outputs (dist/ and node_modules/.vite cache)."
    workingDir(layout.projectDirectory.dir("kdiab-ui"))
    commandLine("bash", "-c", "rm -rf dist node_modules/.vite")
}

tasks.register("clean") {
    group = "build"
    description = "Deletes build outputs for all service backends and the kdiab-ui frontend."
    dependsOn(cleanFrontend)
    dependsOn(serviceBuilds.map { gradle.includedBuild(it).task(":clean") })
}

// -- Docker --------------------------------------------------------------------
// (#613) Docker images are built sequentially in a single podman compose call to avoid
// running multiple compose build processes in parallel, which can exhaust available RAM.
// For a full sequential build use: ./gradlew clean dockerBuild --no-parallel
tasks.register<Exec>("dockerBuild") {
    group = "podman"
    description = "Builds all kdiab Docker images via docker compose. JARs are pre-built by buildBackends."
    dependsOn(buildBackends)
    workingDir(layout.projectDirectory)
    commandLine(
        "bash", "-c",
        "podman compose build liquibase-measures liquibase-profiles " +
        "liquibase-treatments liquibase-carbs measures-backend profiles-backend treatments-backend " +
        "carbs-backend calc-backend analyze-backend nightscout-backend users-backend kdiab-ui"
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
