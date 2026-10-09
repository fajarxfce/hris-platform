plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.documents.domain)
    implementation(projects.core.storage.domain)
    implementation(projects.modules.leave.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.workforce.domain)
    implementation(projects.core.database)
    implementation(libs.spring.core)
    implementation(libs.jackson.kotlin)
}
