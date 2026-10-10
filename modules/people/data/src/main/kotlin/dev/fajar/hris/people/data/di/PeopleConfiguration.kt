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
    fun getPersonProfile(
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetPersonProfile(
            profiles,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getPersonProfileHistory(
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetPersonProfileHistory(
            profiles,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun savePersonProfile(
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SavePersonProfile(
            profiles,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun createEmployee(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        units: OrganizationRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        CreateEmployee(
            people,
            companies,
            members,
            identities,
            units,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun reviseEmployment(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        units: OrganizationRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        transfers: EmploymentTransferRepository,
        lifecycle: LifecycleRepository,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ReviseEmployment(
            people,
            companies,
            members,
            identities,
            units,
            operations,
            journal,
            transactions,
            transfers,
            lifecycle,
            security,
            clock,
        )

    @Bean
    fun getEmployee(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetEmployee(people, companies, members, identities, transactions, security, clock)

    @Bean
    fun listEmployees(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListEmployees(people, companies, members, identities, transactions, security, clock)

    @Bean
    fun listSelfEmployments(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListSelfEmployments(people, companies, members, identities, transactions, security, clock)

    @Bean
    fun getEmploymentDetails(
        people: PeopleRepository,
        units: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetEmploymentDetails(
            people,
            units,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun employmentHistory(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetEmploymentHistory(people, companies, members, identities, transactions, security, clock)

    @Bean
    fun getEmploymentRevision(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetEmploymentRevision(people, companies, members, identities, transactions, security, clock)

    @Bean
    fun cancelEmploymentRevision(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        CancelEmploymentRevision(
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun transferSource(sql: DSLContext): EmploymentTransferDataSource =
        PostgresEmploymentTransferDataSource(sql)

    @Bean
    fun employmentTransfers(source: EmploymentTransferDataSource): EmploymentTransferRepository =
        StoredEmploymentTransferRepository(source)

    @Bean
    fun getEmploymentTransfers(
        transfers: EmploymentTransferRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetEmploymentTransfers(transfers, people, companies, members, identities, transactions)

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
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SaveLifecycleTemplate(
            lifecycle,
            companies,
            identities,
            members,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun startLifecycleCase(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        StartLifecycleCase(
            lifecycle,
            companies,
            identities,
            people,
            members,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listLifecycleTemplates(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListLifecycleTemplates(
            lifecycle,
            companies,
            identities,
            members,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getLifecycleTemplate(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetLifecycleTemplate(
            lifecycle,
            companies,
            identities,
            members,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getLifecycleCase(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetLifecycleCase(
            lifecycle,
            companies,
            identities,
            members,
            people,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getOffboardingReview(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetOffboardingReview(
            lifecycle,
            companies,
            identities,
            members,
            people,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listLifecycleCases(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListLifecycleCases(
            lifecycle,
            companies,
            identities,
            members,
            people,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getLifecycleHistory(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetLifecycleHistory(
            lifecycle,
            companies,
            identities,
            members,
            people,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listLifecycleAssignees(
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListLifecycleAssignees(companies, identities, members, transactions, security, clock)

    @Bean
    fun listAssignedLifecycleTasks(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListAssignedLifecycleTasks(
            lifecycle,
            companies,
            identities,
            members,
            people,
            transactions,
            security,
            clock,
        )

    @Bean
    fun changeLifecycleTask(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ChangeLifecycleTask(
            lifecycle,
            companies,
            identities,
            members,
            people,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun assignLifecycleTask(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        AssignLifecycleTask(
            lifecycle,
            companies,
            identities,
            people,
            members,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun cancelLifecycleCase(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        CancelLifecycleCase(
            lifecycle,
            companies,
            identities,
            members,
            people,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun completeOnboarding(
        lifecycle: LifecycleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        CompleteOnboarding(
            lifecycle,
            companies,
            identities,
            members,
            people,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

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
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
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
            companies,
            members,
            identities,
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
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ApplyEmployeeImport(
            imports,
            companies,
            members,
            identities,
            jobs,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun resumeEmployeeImport(
        imports: EmployeeImportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ResumeEmployeeImport(
            imports,
            companies,
            members,
            identities,
            jobs,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun cancelEmployeeImport(
        imports: EmployeeImportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        CancelEmployeeImport(
            imports,
            companies,
            members,
            identities,
            jobs,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getEmployeeImport(
        imports: EmployeeImportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
        jobs: JobRepository,
    ) =
        GetEmployeeImport(
            imports,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
            jobs,
        )

    @Bean
    fun listEmployeeImports(
        imports: EmployeeImportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListEmployeeImports(imports, companies, members, identities, transactions, security, clock)

    @Bean
    fun getEmployeeImportRows(
        imports: EmployeeImportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetEmployeeImportRows(
            imports,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getEmployeeImportAttempts(
        imports: EmployeeImportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetEmployeeImportAttempts(
            imports,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getEmployeeImportTemplate(input: EmployeeImportInputRepository) =
        GetEmployeeImportTemplate(input)

    @Bean
    fun advanceEmployeeImportPreview(
        imports: EmployeeImportRepository,
        companies: CompanyRepository,
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
            companies,
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
        companies: CompanyRepository,
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
            companies,
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
