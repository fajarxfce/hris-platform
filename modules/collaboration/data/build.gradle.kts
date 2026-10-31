plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.collaboration.domain)
    implementation(projects.core.database)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.spring.flyway)
}
