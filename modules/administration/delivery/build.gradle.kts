plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.administration.domain)
    implementation(projects.core.http)
}
