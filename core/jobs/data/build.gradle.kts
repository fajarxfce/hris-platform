plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.jobs.domain)
    implementation(projects.core.database)
    implementation(libs.jackson.kotlin)
}
