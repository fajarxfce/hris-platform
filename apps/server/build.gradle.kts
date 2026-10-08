plugins { id("hris.application") }

dependencies {
    implementation(projects.core.database)
    implementation(projects.core.http)
    implementation(libs.spring.actuator)
    implementation(libs.spring.security)
    implementation(libs.jackson.kotlin)
    implementation(kotlin("reflect"))
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.test)
}

springBoot { mainClass.set("dev.fajar.hris.HrisApplicationKt") }
