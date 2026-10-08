plugins { id("hris.kotlin") }

dependencies {
    api(projects.core.domain)
    api(projects.core.mail.domain)
}
