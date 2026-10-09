plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.payroll.domain)
    implementation(projects.core.http)
}
