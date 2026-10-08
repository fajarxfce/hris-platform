plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.storage.domain)
    implementation(projects.core.http)
}
