plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.reporting.domain)
    implementation(projects.modules.administration.domain)
    implementation(projects.core.http)
}
