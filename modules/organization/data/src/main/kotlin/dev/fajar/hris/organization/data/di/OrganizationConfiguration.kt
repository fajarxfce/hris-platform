package dev.fajar.hris.organization.data.di

import dev.fajar.hris.core.database.datasources.OperationReceiptDataSource
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.data.datasources.*
import dev.fajar.hris.organization.data.repositories.StoredCompanyRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.usecases.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class OrganizationConfiguration {
    @Bean
    fun organizationSource(sql: DSLContext): OrganizationDataSource =
        PostgresOrganizationDataSource(sql)

    @Bean
    fun organization(
        source: OrganizationDataSource
    ): dev.fajar.hris.organization.domain.repositories.OrganizationRepository =
        dev.fajar.hris.organization.data.repositories.StoredOrganizationRepository(source)

    @Bean
    fun saveUnit(
        units: dev.fajar.hris.organization.domain.repositories.OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SaveOrganizationUnit(
            units,
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
    fun listUnits(
        units: dev.fajar.hris.organization.domain.repositories.OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListOrganizationUnits(units, companies, members, identities, transactions, security, clock)

    @Bean
    fun getUnit(
        units: dev.fajar.hris.organization.domain.repositories.OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetOrganizationUnit(units, companies, members, identities, transactions, security, clock)

    @Bean fun companySource(sql: DSLContext): CompanyDataSource = PostgresCompanyDataSource(sql)

    @Bean
    fun companies(
        source: CompanyDataSource,
        receipts: OperationReceiptDataSource,
        json: ObjectMapper,
    ): CompanyRepository = StoredCompanyRepository(source, receipts, json)

    @Bean
    fun createCompany(
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
    ) = CreateCompany(companies, identities, transactions, journal)

    @Bean
    fun getCompany(
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetCompany(companies, members, identities, transactions)

    @Bean
    fun updateCompany(
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
    ) = UpdateCompany(companies, members, identities, transactions, journal)
}
