plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.payroll.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.core.database)
    implementation(libs.spring.core)
    implementation(libs.jackson.kotlin)
}
