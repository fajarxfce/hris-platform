plugins { id("hris.kotlin") }

dependencies {
    api(projects.core.domain)
    api(projects.modules.expenses.domain)
    api(projects.modules.leave.domain)
    api(projects.modules.payroll.domain)
    api(projects.modules.workforce.domain)
    implementation(projects.modules.administration.domain)
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.organization.domain)
}
