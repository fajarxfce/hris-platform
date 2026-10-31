plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.collaboration.domain)
    implementation(projects.modules.administration.domain)
    implementation(projects.modules.identity.delivery)
    implementation(projects.core.http)
    implementation(libs.spring.security)
}
