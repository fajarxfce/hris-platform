plugins { id("hris.application") }

dependencies {
    implementation(projects.modules.identity.domain)
    implementation(projects.modules.identity.data)
    implementation(projects.modules.identity.delivery)
    implementation(libs.spring.session)
    implementation(projects.core.database)
    implementation(projects.core.http)
    implementation(libs.spring.actuator)
    implementation(libs.spring.security)
    implementation(libs.jackson.kotlin)
    implementation(kotlin("reflect"))
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.webmvc.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}

springBoot { mainClass.set("dev.fajar.hris.HrisApplicationKt") }
