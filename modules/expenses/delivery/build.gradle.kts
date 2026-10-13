plugins { id("hris.spring") }

dependencies {
    implementation(libs.swagger.annotations)
    implementation(projects.modules.documents.domain)
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.expenses.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.core.http)
}
