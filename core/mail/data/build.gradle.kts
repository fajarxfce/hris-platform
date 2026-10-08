plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.mail.domain)
    implementation(libs.spring.mail)
    implementation(libs.angus.mail)
    testImplementation(libs.spring.test)
}
