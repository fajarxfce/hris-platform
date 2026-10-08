plugins { id("hris.spring") }

dependencies {
    testImplementation(libs.spring.test)
    api(projects.core.domain)
    api(libs.spring.webmvc)
    implementation(libs.spring.validation)
}
