plugins { id("hris.kotlin") }

dependencies {
    api(projects.core.domain)
    implementation(projects.core.jobs.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.organization.domain)
}
