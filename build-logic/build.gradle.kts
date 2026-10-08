plugins { `kotlin-dsl` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.kotlin.spring)
    implementation(libs.spring.boot.gradle)
}
