package dev.fajar.hris.worker

import dev.fajar.hris.core.database.DatabaseConfiguration
import dev.fajar.hris.identity.data.di.IdentityConfiguration
import dev.fajar.hris.jobs.data.di.JobsConfiguration
import dev.fajar.hris.organization.data.di.OrganizationConfiguration
import dev.fajar.hris.people.data.di.PeopleConfiguration
import dev.fajar.hris.workforce.data.di.WorkforceConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(
    DatabaseConfiguration::class,
    dev.fajar.hris.mail.data.di.MailConfiguration::class,
    IdentityConfiguration::class,
    JobsConfiguration::class,
    OrganizationConfiguration::class,
    PeopleConfiguration::class,
    WorkforceConfiguration::class,
)
class WorkerApplication

fun main(args: Array<String>) {
    SpringApplicationBuilder(WorkerApplication::class.java).web(WebApplicationType.NONE).run(*args)
}
