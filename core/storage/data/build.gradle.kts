plugins { id("hris.spring") }

dependencies {
    implementation(projects.core.storage.domain)
    implementation(projects.core.database)
    implementation(libs.aws.s3)
    implementation(libs.aws.apache5)
    implementation(libs.spring.core)
    implementation(libs.slf4j.api)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.core)
}
