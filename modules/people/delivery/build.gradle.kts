plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.jobs.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.administration.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.core.http)
}
