plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.people.domain)
    implementation(projects.core.http)
}
