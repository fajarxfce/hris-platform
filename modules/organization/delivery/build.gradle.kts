plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.organization.domain)
    implementation(projects.core.http)
}
