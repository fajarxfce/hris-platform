import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val postgresImage = catalog.findVersion("postgres-image").get().requiredVersion

require(Regex("postgres:[a-zA-Z0-9._-]+@sha256:[0-9a-f]{64}").matches(postgresImage))

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    "implementation"(platform(catalog.findLibrary("spring-boot-bom").get()))
    "testImplementation"(catalog.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(catalog.findLibrary("junit-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Testcontainers 2.0.5 treats a combined tag/digest's tag as part of the repository.
    // Docker's digest-only spelling selects the identical image and preserves compatibility checks.
    systemProperty("hris.postgres.image", "postgres@" + postgresImage.substringAfter('@'))
    maxParallelForks = 1
    maxHeapSize = "768m"
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

base { archivesName.set(project.path.removePrefix(":").replace(":", "-")) }
