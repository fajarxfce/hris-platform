plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.push.domain)
    implementation(libs.google.auth)
    implementation(libs.jackson.kotlin)
    implementation(libs.spring.core)
    implementation(libs.slf4j.api)
    testImplementation(libs.spring.test)
}
