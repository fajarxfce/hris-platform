package dev.fajar.hris.people.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.data.datasources.*
import dev.fajar.hris.people.data.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.people.domain.usecases.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class PeopleConfiguration {
    @Bean fun peopleSource(sql: DSLContext): PeopleDataSource = PostgresPeopleDataSource(sql)

    @Bean
    fun profilesSource(sql: DSLContext): PersonProfileDataSource =
        PostgresPersonProfileDataSource(sql)

    @Bean
    fun profiles(source: PersonProfileDataSource): PersonProfileRepository =
        StoredPersonProfileRepository(source)

    @Bean
    fun people(source: PeopleDataSource, profiles: PersonProfileDataSource): PeopleRepository =
        StoredPeopleRepository(source, profiles)

    @Bean
    fun getPersonProfile(profiles: PersonProfileRepository, transactions: TransactionRunner) =
        GetPersonProfile(profiles, transactions)

    @Bean
    fun getPersonProfileHistory(
        profiles: PersonProfileRepository,
        transactions: TransactionRunner,
    ) = GetPersonProfileHistory(profiles, transactions)

    @Bean
    fun savePersonProfile(
        profiles: PersonProfileRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = SavePersonProfile(profiles, operations, journal, transactions, clock)

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
    fun getEmployee(people: PeopleRepository, transactions: TransactionRunner, clock: Clock) =
        GetEmployee(people, transactions, clock)

    @Bean
    fun listEmployees(people: PeopleRepository, transactions: TransactionRunner, clock: Clock) =
        ListEmployees(people, transactions, clock)

    @Bean
    fun employmentHistory(people: PeopleRepository, transactions: TransactionRunner) =
        GetEmploymentHistory(people, transactions)
}
