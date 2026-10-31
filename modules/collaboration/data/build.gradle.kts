plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.collaboration.domain)
    implementation(projects.core.database)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.spring.flyway)
    testImplementation(projects.modules.identity.data)
    testImplementation(projects.modules.identity.domain)
    testImplementation(projects.modules.administration.data)
    testImplementation(projects.modules.administration.domain)
    testImplementation(projects.modules.organization.data)
    testImplementation(projects.modules.organization.domain)
}
