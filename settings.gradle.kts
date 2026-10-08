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

include(":modules:people:domain", ":modules:people:data", ":modules:people:delivery")

include(":modules:approvals:domain", ":modules:approvals:data", ":modules:approvals:delivery")

include(":modules:workforce:domain", ":modules:workforce:data", ":modules:workforce:delivery")

include(":modules:leave:domain", ":modules:leave:data", ":modules:leave:delivery")

include(":core:jobs:domain", ":core:jobs:data", ":core:jobs:delivery")
