plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.organization.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.core.database)
    implementation(libs.jackson.kotlin)
    implementation(libs.spring.core)
}
