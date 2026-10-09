plugins { id("hris.spring") }

dependencies {
    implementation(libs.pdfbox)
    implementation(projects.modules.approvals.domain)
    implementation(projects.modules.payroll.domain)
    implementation(projects.modules.people.domain)
    implementation(projects.core.jobs.domain)
    implementation(projects.core.http)
}
