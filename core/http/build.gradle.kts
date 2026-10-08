plugins { id("hris.spring") }

dependencies {
    api(projects.core.domain)
    api(libs.spring.webmvc)
    implementation(libs.spring.validation)
}
