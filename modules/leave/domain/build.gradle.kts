plugins { id("hris.kotlin") }

dependencies {
    api(projects.core.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.workforce.domain)
}
