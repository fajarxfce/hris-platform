plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.workforce.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.core.database)
    implementation(libs.spring.core)
    implementation(libs.jackson.kotlin)
}
