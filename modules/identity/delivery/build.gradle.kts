plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.identity.domain)
    implementation(projects.core.http)
    implementation(libs.spring.security)
    implementation(libs.spring.oauth2.client)
    implementation(libs.jackson.kotlin)
}
