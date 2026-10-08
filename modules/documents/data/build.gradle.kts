plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.documents.domain)
    implementation(projects.core.database)
    implementation(projects.core.storage.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
    implementation(libs.spring.core)
}
