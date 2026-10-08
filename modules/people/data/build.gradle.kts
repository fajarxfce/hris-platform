plugins { id("hris.spring") }

dependencies {
    implementation(libs.commons.csv)
    implementation(libs.commons.io)
    implementation(projects.core.jobs.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.modules.organization.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.core.database)
    implementation(libs.spring.core)
    implementation(libs.jackson.kotlin)
}
