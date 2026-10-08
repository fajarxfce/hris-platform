package dev.fajar.hris.people.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.data.datasources.*
import dev.fajar.hris.people.data.repositories.StoredPeopleRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.usecases.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class PeopleConfiguration {
    @Bean fun peopleSource(sql: DSLContext): PeopleDataSource = PostgresPeopleDataSource(sql)

    @Bean fun people(source: PeopleDataSource): PeopleRepository = StoredPeopleRepository(source)

    @Bean
    fun createEmployee(
        people: PeopleRepository,
        units: OrganizationRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = CreateEmployee(people, units, identities, operations, journal, transactions, clock)

    @Bean
    fun reviseEmployment(
        people: PeopleRepository,
        units: OrganizationRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = ReviseEmployment(people, units, operations, journal, transactions)

    @Bean
    fun getEmployee(people: PeopleRepository, transactions: TransactionRunner) =
        GetEmployee(people, transactions)

    @Bean
    fun listEmployees(people: PeopleRepository, transactions: TransactionRunner) =
        ListEmployees(people, transactions)

    @Bean
    fun employmentHistory(people: PeopleRepository, transactions: TransactionRunner) =
        GetEmploymentHistory(people, transactions)
}
