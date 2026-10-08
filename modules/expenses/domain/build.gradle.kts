plugins { id("hris.kotlin") }

dependencies {
    implementation(projects.modules.documents.domain)
    api(projects.core.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
}
