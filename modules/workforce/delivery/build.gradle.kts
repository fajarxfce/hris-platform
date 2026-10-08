plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.workforce.domain)
    implementation(projects.core.http)
}
