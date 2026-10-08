plugins { id("hris.kotlin") }

dependencies {
    implementation(projects.core.storage.domain)
    implementation(projects.modules.documents.domain)
    implementation(projects.modules.approvals.domain)
    api(projects.core.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
}
