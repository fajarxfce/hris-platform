plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.jobs.domain)
    implementation(projects.core.http)
    implementation(libs.spring.security)
}
