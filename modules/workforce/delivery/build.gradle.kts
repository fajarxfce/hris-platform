plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.people.domain)

    implementation(projects.modules.workforce.domain)
    implementation(projects.core.http)
    implementation(libs.swagger.annotations)
}
