plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.core.database)
    implementation(libs.spring.core)
    implementation(libs.jackson.kotlin)
}
