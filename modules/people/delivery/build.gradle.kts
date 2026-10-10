plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.jobs.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.core.http)
}
