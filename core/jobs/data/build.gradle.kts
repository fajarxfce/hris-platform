plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.jobs.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.core.database)
    implementation(libs.jackson.kotlin)
}
