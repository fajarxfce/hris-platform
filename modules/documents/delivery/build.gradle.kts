plugins { id("hris.spring") }

dependencies {
    implementation(libs.swagger.annotations)
    implementation(projects.modules.documents.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.core.http)
}
