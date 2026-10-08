pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "hris-platform"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":core:domain", ":core:database", ":core:http", ":apps:server")

include(":modules:identity:domain", ":modules:identity:data", ":modules:identity:delivery")

include(
    ":modules:organization:domain",
    ":modules:organization:data",
    ":modules:organization:delivery",
)
