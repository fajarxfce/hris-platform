plugins { id("hris.kotlin") }

dependencies {
    implementation(projects.modules.payroll.domain)
    implementation(projects.core.jobs.domain)
    implementation(projects.modules.documents.domain)
    implementation(projects.core.storage.domain)
    api(projects.core.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.workforce.domain)
}
