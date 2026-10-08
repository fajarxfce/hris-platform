plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.storage.domain)
    implementation(projects.modules.documents.domain)
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.expenses.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.core.database)
    implementation(libs.spring.core)
}
