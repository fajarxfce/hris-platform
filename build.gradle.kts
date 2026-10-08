plugins { base }

val formatter by
    configurations.creating {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
        }
    }

dependencies { formatter(libs.ktfmt) }

tasks.register<Exec>("architectureCheck") {
    group = "verification"
    commandLine("python3", "tool/check_architecture.py")
}

tasks.register<JavaExec>("formatCheck") {
    group = "verification"
    classpath = formatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style", "--dry-run", "--set-exit-if-changed")
    doFirst {
        args(
            fileTree(rootDir) {
                    include("**/*.kt", "**/*.kts")
                    exclude("**/build/**", "**/.gradle/**", ".work/**", "**/node_modules/**")
                }
                .files
                .sorted()
                .map { it.absolutePath }
        )
    }
}

tasks.register<JavaExec>("format") {
    classpath = formatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style")
    doFirst {
        args(
            fileTree(rootDir) {
                    include("**/*.kt", "**/*.kts")
                    exclude("**/build/**", "**/.gradle/**", ".work/**", "**/node_modules/**")
                }
                .files
                .sorted()
                .map { it.absolutePath }
        )
    }
}

tasks.named("check") {
    dependsOn("architectureCheck", "formatCheck")
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:check" })
}
