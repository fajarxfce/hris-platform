package dev.fajar.hris.collaboration.data.di

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationRegistryRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationServicePolicy
import dev.fajar.hris.collaboration.domain.repositories.CollaborationRegistryRepository
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration(proxyBeanMethods = false)
class CollaborationConfiguration {
    @Bean
    fun collaborationServicePolicy(environment: Environment) =
        CollaborationServicePolicy(
            environment.getProperty("HRIS_COLLABORATION_ENABLED", Boolean::class.java, false)
        )

    @Bean
    fun collaborationRegistrySource(sql: DSLContext): CollaborationRegistryDataSource =
        PostgresCollaborationRegistryDataSource(sql)

    @Bean
    fun collaborationRegistry(
        source: CollaborationRegistryDataSource
    ): CollaborationRegistryRepository = StoredCollaborationRegistryRepository(source)

    @Bean
    fun registerNativeCollaborationDevice(
        registry: CollaborationRegistryRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
        service: CollaborationServicePolicy,
    ) =
        RegisterNativeCollaborationDevice(
            registry,
            sessions,
            policies,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
            service,
        )

    @Bean
    fun acquireNativeCollaborationAdmission(
        registry: CollaborationRegistryRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
        service: CollaborationServicePolicy,
    ) =
        AcquireNativeCollaborationAdmission(
            registry,
            sessions,
            policies,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
            service,
        )
}
