plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.documents.domain)
    implementation(projects.core.http)
}
