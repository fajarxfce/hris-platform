plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.leave.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.core.http)
}
