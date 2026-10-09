package dev.fajar.hris.administration.data.di

import dev.fajar.hris.administration.data.datasources.*
import dev.fajar.hris.administration.data.repositories.StoredCompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AdministrationConfiguration {
    @Bean
    fun companyClientPolicySource(sql: DSLContext): CompanyClientPolicyDataSource =
        PostgresCompanyClientPolicyDataSource(sql)

    @Bean
    fun companyClientPolicies(
        source: CompanyClientPolicyDataSource
    ): CompanyClientPolicyRepository = StoredCompanyClientPolicyRepository(source)

    @Bean
    fun saveCompanyClientPolicy(
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SaveCompanyClientPolicy(
            policies,
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
    fun getCompanyClientPolicyRevision(
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetCompanyClientPolicyRevision(policies, companies, members, identities, transactions)

    @Bean
    fun getClientPolicy(
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetClientPolicy(policies, companies, members, identities, transactions, clock)

    @Bean
    fun checkCompanyAvailability(
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = CheckCompanyAvailability(policies, companies, members, identities, transactions, clock)
}
