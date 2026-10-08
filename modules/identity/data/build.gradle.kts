plugins { id("hris.spring") }

dependencies {
    implementation(projects.modules.identity.domain)
    implementation(projects.core.database)
    implementation(libs.spring.security)
    implementation(libs.bouncycastle)
    implementation(libs.jackson.kotlin)
}
