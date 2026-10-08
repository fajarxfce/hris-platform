plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.storage.domain)
    implementation(libs.aws.s3)
    implementation(libs.aws.url.connection)
    implementation(libs.spring.core)
    implementation(libs.slf4j.api)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.core)
}
