plugins { id("hris.spring") }

val jooqGenerator by configurations.creating

dependencies {
    api(projects.core.domain)
    api(libs.jooq)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.jooq)
    implementation(libs.jackson.kotlin)
    implementation(libs.spring.flyway)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    jooqGenerator(platform(libs.spring.boot.bom))
    jooqGenerator(libs.jooq.codegen)
    jooqGenerator(libs.postgresql)
}

val generated = layout.buildDirectory.dir("generated/jooq")
val generateJooq by
    tasks.registering(Exec::class) {
        group = "code generation"
        inputs.dir("src/main/resources/db/migration")
        inputs.file(rootProject.file("tool/generate_jooq.py"))
        inputs.files(jooqGenerator)
        outputs.dir(generated)
        doFirst {
            commandLine(
                "python3",
                rootProject.file("tool/generate_jooq.py").absolutePath,
                jooqGenerator.asPath,
                generated.get().asFile.absolutePath,
                file("src/main/resources/db/migration").absolutePath,
            )
        }
    }

sourceSets.main { java.srcDir(generated) }

tasks.named("compileKotlin") { dependsOn(generateJooq) }
