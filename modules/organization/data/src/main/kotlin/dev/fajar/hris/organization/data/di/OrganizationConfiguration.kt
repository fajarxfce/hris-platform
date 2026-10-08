package dev.fajar.hris.organization.data.di

import dev.fajar.hris.core.database.datasources.OperationReceiptDataSource
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.organization.data.datasources.*
import dev.fajar.hris.organization.data.repositories.StoredCompanyRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.usecases.*
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
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SaveOrganizationUnit(units, operations, journal, transactions)

    @Bean
    fun listUnits(
        units: dev.fajar.hris.organization.domain.repositories.OrganizationRepository,
        transactions: TransactionRunner,
    ) = ListOrganizationUnits(units, transactions)

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
    fun getCompany(companies: CompanyRepository, transactions: TransactionRunner) =
        GetCompany(companies, transactions)

    @Bean
    fun updateCompany(
        companies: CompanyRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
    ) = UpdateCompany(companies, transactions, journal)
}
