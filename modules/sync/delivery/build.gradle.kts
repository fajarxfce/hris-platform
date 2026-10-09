plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.sync.domain)
    implementation(projects.core.http)
}
