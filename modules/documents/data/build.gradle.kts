plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.jobs.domain)
    implementation(projects.core.storage.data)
    implementation(libs.tika.core)
    implementation(libs.slf4j.api)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    implementation(projects.modules.documents.domain)
    implementation(projects.core.database)
    implementation(projects.core.storage.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
    implementation(libs.spring.core)
}
