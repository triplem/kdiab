plugins {
    alias(libs.plugins.asciidoctor)
}

repositories {
    mavenCentral()
}

tasks.asciidoctor {
    baseDirFollowsSourceFile()
    sourceDir(file("docs"))
    setOutputDir(file("build/docs/asciidoc"))
    attributes(
        mapOf(
            "toc" to "left",
            "icons" to "font",
            "source-highlighter" to "rouge"
        )
    )
}

tasks.register<Exec>("buildFrontend") {
    workingDir = file("frontend")
    commandLine("bash", "-c", "npm ci --silent && npm run build")
    inputs.dir("frontend/src")
    inputs.files("frontend/package-lock.json", "frontend/package.json")
    outputs.dir("frontend/dist")
}

tasks.register<Exec>("checkFrontend") {
    workingDir = file("frontend")
    commandLine("bash", "-c", "npm ci --silent && npm run lint && npm run test")
}

tasks.register("buildAll") {
    dependsOn(":backend:build", "buildFrontend")
}

tasks.register("checkAll") {
    dependsOn(":backend:check", "checkFrontend")
}

tasks.register("cleanAll") {
    dependsOn(":backend:clean")
    doLast {
        delete("frontend/dist")
    }
}
