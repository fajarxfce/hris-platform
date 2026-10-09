plugins { id("hris.kotlin") }

dependencies {
    implementation(projects.core.jobs.domain)
    api(projects.core.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.modules.people.domain)
}
