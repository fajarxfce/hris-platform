package dev.fajar.hris.people.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.data.datasources.*
import dev.fajar.hris.people.data.repositories.*
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class PeopleConfiguration {
    @Bean
    fun bindPersonAccount(
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        accounts: AccountAdministrationRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        BindPersonAccount(
            profiles,
            people,
            companies,
            identities,
            accounts,
            members,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

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
        transfers: EmploymentTransferRepository,
        lifecycle: LifecycleRepository,
    ) = ReviseEmployment(people, units, operations, journal, transactions, transfers, lifecycle)

    @Bean
    fun getEmployee(people: PeopleRepository, transactions: TransactionRunner, clock: Clock) =
        GetEmployee(people, transactions, clock)

    @Bean
    fun listEmployees(people: PeopleRepository, transactions: TransactionRunner, clock: Clock) =
        ListEmployees(people, transactions, clock)

    @Bean
    fun employmentHistory(people: PeopleRepository, transactions: TransactionRunner) =
        GetEmploymentHistory(people, transactions)

    @Bean
    fun cancelEmploymentRevision(
        people: PeopleRepository,
        companies: CompanyRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = CancelEmploymentRevision(people, companies, operations, journal, transactions, clock)

    @Bean
    fun transferSource(sql: DSLContext): EmploymentTransferDataSource =
        PostgresEmploymentTransferDataSource(sql)

    @Bean
    fun employmentTransfers(source: EmploymentTransferDataSource): EmploymentTransferRepository =
        StoredEmploymentTransferRepository(source)

    @Bean
    fun getEmploymentTransfers(
        transfers: EmploymentTransferRepository,
        transactions: TransactionRunner,
    ) = GetEmploymentTransfers(transfers, transactions)

    @Bean
    fun transferEmployee(
        people: PeopleRepository,
        transfers: EmploymentTransferRepository,
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        units: OrganizationRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        roles: RoleTemplateRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: CrossCompanyTransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        TransferEmployee(
            people,
            lifecycle,
            transfers,
            companies,
            units,
            identities,
            members,
            roles,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun lifecycleSource(sql: DSLContext): LifecycleDataSource = PostgresLifecycleDataSource(sql)

    @Bean
    fun lifecycle(
        source: LifecycleDataSource,
        json: tools.jackson.databind.ObjectMapper,
    ): LifecycleRepository = StoredLifecycleRepository(source, json)

    @Bean
    fun saveLifecycleTemplate(
        lifecycle: LifecycleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SaveLifecycleTemplate(lifecycle, operations, journal, transactions)

    @Bean
    fun startLifecycleCase(
        lifecycle: LifecycleRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = StartLifecycleCase(lifecycle, people, members, operations, journal, transactions, clock)

    @Bean
    fun listLifecycleTemplates(lifecycle: LifecycleRepository, transactions: TransactionRunner) =
        ListLifecycleTemplates(lifecycle, transactions)

    @Bean
    fun getLifecycleCase(lifecycle: LifecycleRepository, transactions: TransactionRunner) =
        GetLifecycleCase(lifecycle, transactions)

    @Bean
    fun listLifecycleCases(lifecycle: LifecycleRepository, transactions: TransactionRunner) =
        ListLifecycleCases(lifecycle, transactions)

    @Bean
    fun getLifecycleHistory(lifecycle: LifecycleRepository, transactions: TransactionRunner) =
        GetLifecycleHistory(lifecycle, transactions)

    @Bean
    fun listAssignedLifecycleTasks(
        lifecycle: LifecycleRepository,
        transactions: TransactionRunner,
    ) = ListAssignedLifecycleTasks(lifecycle, transactions)

    @Bean
    fun changeLifecycleTask(
        lifecycle: LifecycleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ChangeLifecycleTask(lifecycle, operations, journal, transactions, clock)

    @Bean
    fun assignLifecycleTask(
        lifecycle: LifecycleRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = AssignLifecycleTask(lifecycle, members, operations, journal, transactions, clock)

    @Bean
    fun cancelLifecycleCase(
        lifecycle: LifecycleRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = CancelLifecycleCase(lifecycle, people, operations, journal, transactions, clock)

    @Bean
    fun completeOnboarding(
        lifecycle: LifecycleRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = CompleteOnboarding(lifecycle, people, operations, journal, transactions, clock)

    @Bean
    fun completeOffboarding(
        lifecycle: LifecycleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        roles: RoleTemplateRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        CompleteOffboarding(
            lifecycle,
            people,
            companies,
            identities,
            members,
            roles,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean fun employeeCsvSource(): EmployeeCsvDataSource = CommonsEmployeeCsvDataSource()

    @Bean
    fun employeeImportInput(source: EmployeeCsvDataSource): EmployeeImportInputRepository =
        CsvEmployeeImportInputRepository(source)

    @Bean
    fun employeeImportSource(sql: DSLContext): EmployeeImportDataSource =
        PostgresEmployeeImportDataSource(sql)

    @Bean
    fun employeeImports(
        source: EmployeeImportDataSource,
        json: tools.jackson.databind.ObjectMapper,
    ): EmployeeImportRepository = StoredEmployeeImportRepository(source, json)

    @Bean
    fun startEmployeeImport(
        input: EmployeeImportInputRepository,
        imports: EmployeeImportRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        StartEmployeeImport(
            input,
            imports,
            jobs,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun applyEmployeeImport(
        imports: EmployeeImportRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = ApplyEmployeeImport(imports, jobs, operations, journal, transactions, clock, security)

    @Bean
    fun resumeEmployeeImport(
        imports: EmployeeImportRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = ResumeEmployeeImport(imports, jobs, operations, journal, transactions, clock, security)

    @Bean
    fun cancelEmployeeImport(
        imports: EmployeeImportRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = CancelEmployeeImport(imports, jobs, operations, journal, transactions, clock, security)

    @Bean
    fun getEmployeeImport(imports: EmployeeImportRepository, transactions: TransactionRunner) =
        GetEmployeeImport(imports, transactions)

    @Bean
    fun listEmployeeImports(imports: EmployeeImportRepository, transactions: TransactionRunner) =
        ListEmployeeImports(imports, transactions)

    @Bean
    fun getEmployeeImportRows(imports: EmployeeImportRepository, transactions: TransactionRunner) =
        GetEmployeeImportRows(imports, transactions)

    @Bean
    fun getEmployeeImportAttempts(
        imports: EmployeeImportRepository,
        transactions: TransactionRunner,
    ) = GetEmployeeImportAttempts(imports, transactions)

    @Bean
    fun getEmployeeImportTemplate(input: EmployeeImportInputRepository) =
        GetEmployeeImportTemplate(input)

    @Bean
    fun advanceEmployeeImportPreview(
        imports: EmployeeImportRepository,
        jobs: JobRepository,
        people: PeopleRepository,
        units: OrganizationRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        AdvanceEmployeeImportPreview(
            imports,
            jobs,
            people,
            units,
            identities,
            members,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun advanceEmployeeImportApply(
        imports: EmployeeImportRepository,
        jobs: JobRepository,
        people: PeopleRepository,
        units: OrganizationRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        AdvanceEmployeeImportApply(
            imports,
            jobs,
            people,
            units,
            identities,
            members,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun abortEmployeeImport(
        imports: EmployeeImportRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortEmployeeImport(imports, jobs, journal, transactions)
}
