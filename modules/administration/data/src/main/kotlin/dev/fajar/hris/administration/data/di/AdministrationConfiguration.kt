package dev.fajar.hris.administration.data.di

import dev.fajar.hris.administration.data.datasources.*
import dev.fajar.hris.administration.data.repositories.ConfiguredIntegrationRepository
import dev.fajar.hris.administration.data.repositories.StoredAuditRepository
import dev.fajar.hris.administration.data.repositories.StoredCompanyClientPolicyRepository
import dev.fajar.hris.administration.data.repositories.StoredPlatformAuditRepository
import dev.fajar.hris.administration.data.repositories.StoredWorkerIntegrationRepository
import dev.fajar.hris.administration.domain.entities.IntegrationKind
import dev.fajar.hris.administration.domain.repositories.AuditRepository
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.repositories.IntegrationConfigurationRepository
import dev.fajar.hris.administration.domain.repositories.PlatformAuditRepository
import dev.fajar.hris.administration.domain.repositories.WorkerIntegrationRepository
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
    fun integrationConfiguration(
        environment: org.springframework.core.env.Environment
    ): IntegrationConfigurationRepository =
        ConfiguredIntegrationRepository(
            mapOf(
                    IntegrationKind.FIREBASE_AUTH to "HRIS_FIREBASE_AUTH_ENABLED",
                    IntegrationKind.OIDC to "HRIS_OIDC_ENABLED",
                    IntegrationKind.MAIL to "HRIS_MAIL_ENABLED",
                    IntegrationKind.PUSH to "HRIS_FCM_ENABLED",
                    IntegrationKind.STORAGE to "HRIS_STORAGE_ENABLED",
                    IntegrationKind.DOCUMENT_SCANNER to "HRIS_DOCUMENT_SCANNER_ENABLED",
                )
                .filterValues { environment.getProperty(it, Boolean::class.java, false) }
                .keys
        )

    @Bean
    fun getIntegrationConfiguration(
        configuration: IntegrationConfigurationRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetIntegrationConfiguration(configuration, identities, transactions, security, clock)

    @Bean
    fun workerIntegrationSource(sql: DSLContext): WorkerIntegrationDataSource =
        PostgresWorkerIntegrationDataSource(sql)

    @Bean
    fun workerIntegrations(source: WorkerIntegrationDataSource): WorkerIntegrationRepository =
        StoredWorkerIntegrationRepository(source)

    @Bean
    fun publishWorkerIntegrationObservation(
        configuration: IntegrationConfigurationRepository,
        registry: WorkerIntegrationRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = PublishWorkerIntegrationObservation(configuration, registry, transactions, clock)

    @Bean
    fun getWorkerIntegrationRegistry(
        registry: WorkerIntegrationRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetWorkerIntegrationRegistry(registry, identities, transactions, security, clock)

    @Bean
    fun platformAuditSource(sql: DSLContext): PlatformAuditDataSource =
        PostgresPlatformAuditDataSource(sql)

    @Bean
    fun platformAudits(source: PlatformAuditDataSource): PlatformAuditRepository =
        StoredPlatformAuditRepository(source)

    @Bean
    fun searchPlatformAuditEvents(
        audits: PlatformAuditRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = SearchPlatformAuditEvents(audits, identities, transactions, security, clock)

    @Bean
    fun getPlatformAccountAccessAudit(
        audits: PlatformAuditRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetPlatformAccountAccessAudit(audits, identities, transactions, security, clock)

    @Bean fun auditSource(sql: DSLContext): AuditDataSource = PostgresAuditDataSource(sql)

    @Bean fun audits(source: AuditDataSource): AuditRepository = StoredAuditRepository(source)

    @Bean
    fun searchAuditEvents(
        audits: AuditRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = SearchAuditEvents(audits, companies, members, identities, transactions, security, clock)

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
    fun getCompanyClientPolicySettings(
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetCompanyClientPolicySettings(
            policies,
            companies,
            members,
            identities,
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
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetCompanyClientPolicyRevision(
            policies,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

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
