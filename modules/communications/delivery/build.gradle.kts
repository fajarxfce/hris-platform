plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.communications.domain)
    implementation(projects.core.http)
}
