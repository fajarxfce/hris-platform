plugins { id("hris.kotlin") }

dependencies {
    api(projects.core.domain)
    api(projects.core.jobs.domain)
    api(projects.core.storage.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
}
