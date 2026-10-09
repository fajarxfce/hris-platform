plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.reporting.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.core.database)
    implementation(libs.spring.core)
}
