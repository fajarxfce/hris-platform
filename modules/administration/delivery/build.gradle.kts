plugins { id("hris.spring") }

dependencies {
    implementation(libs.swagger.annotations)
    implementation(projects.modules.administration.domain)
    implementation(projects.core.http)
}
