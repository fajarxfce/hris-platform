plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.collaboration.domain)
    implementation(projects.core.database)
    implementation(projects.core.jobs.domain)
    testImplementation(projects.core.jobs.data)
    implementation(libs.jackson.kotlin)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.identity.data)
    implementation(projects.modules.administration.domain)
    implementation(projects.modules.organization.domain)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.spring.flyway)
    testImplementation(projects.modules.administration.data)
    testImplementation(projects.modules.organization.data)
}
