plugins { id("hris.kotlin") }

dependencies {
    api(projects.core.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.modules.identity.domain)
}
