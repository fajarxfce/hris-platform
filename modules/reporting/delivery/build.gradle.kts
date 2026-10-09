plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.reporting.domain)
    implementation(projects.core.http)
}
