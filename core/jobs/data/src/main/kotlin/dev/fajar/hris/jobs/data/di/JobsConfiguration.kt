package dev.fajar.hris.jobs.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.data.datasources.*
import dev.fajar.hris.jobs.data.repositories.PostgresJobRepository
import dev.fajar.hris.jobs.domain.entities.JobRetryPolicy
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.jobs.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class JobsConfiguration {
    @Bean fun jobSource(sql: DSLContext): JobDataSource = PostgresJobDataSource(sql)

    @Bean
    fun jobs(source: JobDataSource, json: ObjectMapper): JobRepository =
        PostgresJobRepository(source, json)

    @Bean fun jobRetryPolicy() = JobRetryPolicy()

    @Bean
    fun getJob(
        jobs: JobRepository,
        transactions: TransactionRunner,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetJob(jobs, transactions, companies, members, identities, security, clock)

    @Bean
    fun listJobs(
        jobs: JobRepository,
        transactions: TransactionRunner,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListJobs(jobs, transactions, companies, members, identities, security, clock)

    @Bean
    fun requestJobCancellation(
        jobs: JobRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        RequestJobCancellation(
            jobs,
            transactions,
            journal,
            companies,
            members,
            identities,
            security,
            clock,
        )

    @Bean
    fun leaseJobs(
        jobs: JobRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
        clock: Clock,
        retry: JobRetryPolicy,
    ) = LeaseJobs(jobs, transactions, journal, clock, retry)

    @Bean
    fun keepJobAlive(jobs: JobRepository, transactions: TransactionRunner) =
        KeepJobAlive(jobs, transactions)

    @Bean
    fun deferJob(jobs: JobRepository, transactions: TransactionRunner, policy: JobRetryPolicy) =
        DeferJob(jobs, transactions, policy)
}
