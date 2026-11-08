package dev.fajar.hris.collaboration.data.di

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration(proxyBeanMethods = false)
class CollaborationConfiguration {
    @Bean
    fun collaborationSummaryReadSource(sql: DSLContext): CollaborationSummaryReadDataSource =
        PostgresCollaborationSummaryReadDataSource(sql)

    @Bean
    fun collaborationSummaryReads(
        source: CollaborationSummaryReadDataSource
    ): CollaborationSummaryReadRepository = StoredCollaborationSummaryReadRepository(source)

    @Bean
    fun collaborationSummaryCursors(
        crypto: IdentityTokenDataSource
    ): CollaborationSummaryCursorRepository = EncryptedCollaborationSummaryCursorRepository(crypto)

    @Bean
    fun syncNativeCollaborationSummaries(
        summaries: CollaborationSummaryReadRepository,
        cursors: CollaborationSummaryCursorRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        SyncNativeCollaborationSummaries(
            summaries,
            cursors,
            sessions,
            policies,
            companies,
            members,
            identities,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun collaborationSummaryProjectionSource(
        sql: DSLContext
    ): CollaborationSummaryProjectionDataSource =
        PostgresCollaborationSummaryProjectionDataSource(sql)

    @Bean
    fun collaborationSummaryProjections(
        source: CollaborationSummaryProjectionDataSource
    ): CollaborationSummaryProjectionRepository =
        StoredCollaborationSummaryProjectionRepository(source)

    @Bean
    fun refreshCollaborationSummaries(
        projections: CollaborationSummaryProjectionRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = RefreshCollaborationSummaries(projections, transactions, clock)

    @Bean
    fun collaborationPushQueueSource(sql: DSLContext): CollaborationPushQueueDataSource =
        PostgresCollaborationPushQueueDataSource(sql)

    @Bean
    fun collaborationPushQueue(
        source: CollaborationPushQueueDataSource
    ): CollaborationPushQueueRepository = StoredCollaborationPushQueueRepository(source)

    @Bean
    fun handleCollaborationPush(
        bindings: CollaborationPushBindingRepository,
        queue: CollaborationPushQueueRepository,
        gateway: CollaborationGatewaySessionRepository,
        registry: CollaborationRegistryRepository,
        rooms: CollaborationRoomScopeRepository,
        sessions: NativeSessionRepository,
        nativePush: NativePushRegistrationRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
        push: PushRepository,
    ) =
        HandleCollaborationPush(
            bindings,
            queue,
            gateway,
            registry,
            rooms,
            sessions,
            nativePush,
            policies,
            companies,
            members,
            identities,
            transactions,
            service,
            provider,
            clock,
            push,
        )

    @Bean
    @ConditionalOnProperty(
        name =
            [
                "HRIS_COLLABORATION_ENABLED",
                "HRIS_MATRIX_ENABLED",
                "HRIS_COLLABORATION_PUSH_ENABLED",
            ],
        havingValue = "true",
    )
    fun collaborationPushGateway(environment: Environment): MatrixPushGatewayConfiguration =
        matrixPushGatewaySettings(environment)

    @Bean
    fun collaborationMatrixPusher(
        source: ObjectProvider<MatrixPusherDataSource>,
        configuration: ObjectProvider<MatrixPushGatewayConfiguration>,
    ): CollaborationMatrixPusherRepository {
        val gateway = configuration.ifAvailable
        val transport = source.ifAvailable
        return if (gateway != null && transport != null)
            InternalCollaborationMatrixPusherRepository(transport, gateway)
        else DisabledCollaborationMatrixPusherRepository()
    }

    @Bean
    fun collaborationPushBindingSource(sql: DSLContext): CollaborationPushBindingDataSource =
        PostgresCollaborationPushBindingDataSource(sql)

    @Bean
    fun collaborationPushBindings(
        source: CollaborationPushBindingDataSource,
        crypto: IdentityTokenDataSource,
    ): CollaborationPushBindingRepository =
        EncryptedCollaborationPushBindingRepository(source, crypto)

    @Bean
    fun collaborationMatrixRoomMessage(
        source: ObjectProvider<MatrixRoomMessageDataSource>
    ): CollaborationMatrixRoomMessageRepository =
        source.ifAvailable?.let(::InternalCollaborationMatrixRoomMessageRepository)
            ?: DisabledCollaborationMatrixRoomMessageRepository()

    @Bean
    fun collaborationMatrixRoomHistory(
        source: ObjectProvider<MatrixRoomHistoryDataSource>,
        crypto: IdentityTokenDataSource,
    ): CollaborationMatrixRoomHistoryRepository =
        source.ifAvailable?.let { InternalCollaborationMatrixRoomHistoryRepository(it, crypto) }
            ?: DisabledCollaborationMatrixRoomHistoryRepository()

    @Bean
    fun collaborationMatrixRoomAccess(
        source: ObjectProvider<MatrixRoomAccessDataSource>
    ): CollaborationMatrixRoomAccessRepository =
        source.ifAvailable?.let(::InternalCollaborationMatrixRoomAccessRepository)
            ?: DisabledCollaborationMatrixRoomAccessRepository()

    @Bean
    fun collaborationRoomScopeSource(sql: DSLContext): CollaborationRoomScopeDataSource =
        PostgresCollaborationRoomScopeDataSource(sql)

    @Bean
    fun collaborationRoomScopes(
        source: CollaborationRoomScopeDataSource
    ): CollaborationRoomScopeRepository = StoredCollaborationRoomScopeRepository(source)

    @Bean
    fun collaborationSyncCursors(
        crypto: IdentityTokenDataSource
    ): CollaborationSyncCursorRepository = EncryptedCollaborationSyncCursorRepository(crypto)

    @Bean
    fun collaborationMatrixSync(
        source: ObjectProvider<MatrixSyncDataSource>,
        crypto: IdentityTokenDataSource,
    ): CollaborationMatrixSyncRepository =
        source.ifAvailable?.let { InternalCollaborationMatrixSyncRepository(it, crypto) }
            ?: DisabledCollaborationMatrixSyncRepository()

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
        name = ["HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"],
        havingValue = "true",
    )
    fun collaborationMatrixSyncSource(
        owner: MatrixHttpClientOwner,
        configuration: MatrixProviderConfiguration,
        clock: Clock,
    ): HttpMatrixSyncDataSource = HttpMatrixSyncDataSource(owner.client, configuration, clock)

    @Bean
    fun collaborationRecipientSource(sql: DSLContext): CollaborationRecipientDataSource =
        PostgresCollaborationRecipientDataSource(sql)

    @Bean
    fun collaborationRecipients(
        source: CollaborationRecipientDataSource
    ): CollaborationRecipientRepository = StoredCollaborationRecipientRepository(source)

    @Bean
    fun collaborationDeviceKeySource(sql: DSLContext): CollaborationDeviceKeyDataSource =
        PostgresCollaborationDeviceKeyDataSource(sql)

    @Bean
    fun collaborationDeviceKeys(
        source: CollaborationDeviceKeyDataSource
    ): CollaborationDeviceKeyRepository = StoredCollaborationDeviceKeyRepository(source)

    @Bean
    fun collaborationMatrixKeys(
        source: ObjectProvider<MatrixKeyDataSource>
    ): CollaborationMatrixKeyRepository =
        source.ifAvailable?.let(::InternalCollaborationMatrixKeyRepository)
            ?: DisabledCollaborationMatrixKeyRepository()

    @Bean
    fun collaborationMatrixToDevice(
        source: ObjectProvider<MatrixToDeviceDataSource>
    ): CollaborationMatrixToDeviceRepository =
        source.ifAvailable?.let(::InternalCollaborationMatrixToDeviceRepository)
            ?: DisabledCollaborationMatrixToDeviceRepository()

    @Bean
    fun collaborationDirectRoomSource(sql: DSLContext): CollaborationDirectRoomDataSource =
        PostgresCollaborationDirectRoomDataSource(sql)

    @Bean
    fun collaborationDirectRooms(
        source: CollaborationDirectRoomDataSource
    ): CollaborationDirectRoomRepository = StoredCollaborationDirectRoomRepository(source)

    @Bean
    fun requestNativeCollaborationDirectRoom(
        conversations: CollaborationDirectRepository,
        rooms: CollaborationDirectRoomRepository,
        registry: CollaborationRegistryRepository,
        jobs: JobRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        RequestNativeCollaborationDirectRoom(
            conversations,
            rooms,
            registry,
            jobs,
            sessions,
            policies,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun getNativeCollaborationDirectRoomStatus(
        conversations: CollaborationDirectRepository,
        rooms: CollaborationDirectRoomRepository,
        jobs: JobRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        GetNativeCollaborationDirectRoomStatus(
            conversations,
            rooms,
            jobs,
            sessions,
            policies,
            companies,
            members,
            identities,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun readNativeCollaborationDirectTransport(
        conversations: CollaborationDirectRepository,
        rooms: CollaborationDirectRoomRepository,
        registry: CollaborationRegistryRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
        recipients: CollaborationRecipientRepository,
    ) =
        ReadNativeCollaborationDirectTransport(
            conversations,
            rooms,
            registry,
            sessions,
            policies,
            companies,
            members,
            identities,
            transactions,
            security,
            service,
            provider,
            clock,
            recipients,
        )

    @Bean
    fun advanceNativeCollaborationDirectRoom(
        conversations: CollaborationDirectRepository,
        rooms: CollaborationDirectRoomRepository,
        registry: CollaborationRegistryRepository,
        credentials: CollaborationProviderSessionRepository,
        matrix: CollaborationMatrixRepository,
        providerRooms: CollaborationMatrixRoomRepository,
        jobs: JobRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        AdvanceNativeCollaborationDirectRoom(
            conversations,
            rooms,
            registry,
            credentials,
            matrix,
            providerRooms,
            jobs,
            sessions,
            policies,
            companies,
            members,
            identities,
            journal,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun collaborationServicePolicy(environment: Environment) =
        CollaborationServicePolicy(
            environment.getProperty("HRIS_COLLABORATION_ENABLED", Boolean::class.java, false),
            environment.getProperty("HRIS_COLLABORATION_PUSH_ENABLED", Boolean::class.java, false),
        )

    @Bean
    @ConditionalOnProperty(
        name = ["HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"],
        havingValue = "true",
    )
    fun collaborationMatrixConfiguration(
        environment: Environment,
        keyring: dev.fajar.hris.identity.data.crypto.IdentityKeyring,
    ): MatrixProviderConfiguration {
        check(keyring.activeId in keyring.keys) {
            "Matrix provisioning requires the identity encryption keyring"
        }
        return matrixProviderSettings(environment)
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
        name = ["HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"],
        havingValue = "true",
    )
    fun collaborationMatrixHttpOwner() = MatrixHttpClientOwner()

    @Bean
    @ConditionalOnProperty(
        name = ["HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"],
        havingValue = "true",
    )
    fun collaborationMatrixSigning(
        configuration: MatrixProviderConfiguration
    ): MatrixJwtDataSource = HmacMatrixJwtDataSource(configuration)

    @Bean
    @ConditionalOnProperty(
        name = ["HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"],
        havingValue = "true",
    )
    fun collaborationMatrixClient(
        owner: MatrixHttpClientOwner,
        configuration: MatrixProviderConfiguration,
        clock: Clock,
    ): HttpMatrixClientDataSource = HttpMatrixClientDataSource(owner.client, configuration, clock)

    @Bean
    fun collaborationMatrixPolicy(
        configuration: ObjectProvider<MatrixProviderConfiguration>
    ): CollaborationMatrixPolicy =
        configuration.getIfAvailable()?.let { CollaborationMatrixPolicy(true, it.serverName) }
            ?: CollaborationMatrixPolicy(false, "")

    @Bean
    fun collaborationMatrix(
        signing: ObjectProvider<MatrixJwtDataSource>,
        clients: ObjectProvider<MatrixClientDataSource>,
        clock: Clock,
    ): CollaborationMatrixRepository =
        clients.getIfAvailable()?.let {
            InternalCollaborationMatrixRepository(signing.getObject(), it, clock)
        } ?: DisabledCollaborationMatrixRepository()

    @Bean
    fun collaborationSecretStorage(
        clients: ObjectProvider<MatrixSecretStorageDataSource>
    ): CollaborationSecretStorageRepository =
        clients.ifAvailable?.let { InternalCollaborationSecretStorageRepository(it) }
            ?: DisabledCollaborationSecretStorageRepository()

    @Bean
    fun collaborationMatrixRooms(
        clients: ObjectProvider<MatrixRoomDataSource>,
        provider: CollaborationMatrixPolicy,
    ): CollaborationMatrixRoomRepository =
        clients.getIfAvailable()?.let {
            InternalCollaborationMatrixRoomRepository(it, provider.serverName)
        } ?: DisabledCollaborationMatrixRoomRepository()

    @Bean
    fun collaborationGatewaySource(sql: DSLContext): CollaborationGatewaySessionDataSource =
        PostgresCollaborationGatewaySessionDataSource(sql)

    @Bean
    fun collaborationGatewaySessions(
        source: CollaborationGatewaySessionDataSource,
        crypto: IdentityTokenDataSource,
    ): CollaborationGatewaySessionRepository =
        EncryptedCollaborationGatewaySessionRepository(source, crypto)

    @Bean
    fun maintainCollaborationGatewayReplays(
        gateway: CollaborationGatewaySessionRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = MaintainCollaborationGatewayReplays(gateway, transactions, clock)

    @Bean
    fun bootstrapNativeCollaborationGateway(
        gateway: CollaborationGatewaySessionRepository,
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
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        BootstrapNativeCollaborationGateway(
            gateway,
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
            service,
            provider,
            clock,
        )

    @Bean
    fun collaborationDirectSource(sql: DSLContext): CollaborationDirectDataSource =
        PostgresCollaborationDirectDataSource(sql)

    @Bean
    fun collaborationDirectRepository(
        source: CollaborationDirectDataSource
    ): CollaborationDirectRepository = StoredCollaborationDirectRepository(source)

    @Bean
    fun openNativeCollaborationDirect(
        conversations: CollaborationDirectRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        OpenNativeCollaborationDirect(
            conversations,
            sessions,
            policies,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun readNativeCollaborationDirect(
        conversations: CollaborationDirectRepository,
        rooms: CollaborationRoomScopeRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        ReadNativeCollaborationDirect(
            conversations,
            rooms,
            sessions,
            policies,
            companies,
            members,
            identities,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun listNativeCollaborationParticipants(
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        ListNativeCollaborationParticipants(
            sessions,
            policies,
            companies,
            members,
            identities,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun accessNativeCollaborationMatrixDevice(
        gateway: CollaborationGatewaySessionRepository,
        registry: CollaborationRegistryRepository,
        credentials: CollaborationProviderSessionRepository,
        matrix: CollaborationMatrixRepository,
        keys: CollaborationDeviceKeyRepository,
        providerKeys: CollaborationMatrixKeyRepository,
        providerToDevice: CollaborationMatrixToDeviceRepository,
        recipients: CollaborationRecipientRepository,
        conversations: CollaborationDirectRepository,
        roomScopes: CollaborationRoomScopeRepository,
        syncCursors: CollaborationSyncCursorRepository,
        providerSync: CollaborationMatrixSyncRepository,
        providerRoomAccess: CollaborationMatrixRoomAccessRepository,
        providerRoomHistory: CollaborationMatrixRoomHistoryRepository,
        providerRoomMessage: CollaborationMatrixRoomMessageRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
        pushBindings: CollaborationPushBindingRepository,
        providerPush: CollaborationMatrixPusherRepository,
        nativePush: NativePushRegistrationRepository,
        secretStorage: CollaborationSecretStorageRepository,
    ) =
        AccessNativeCollaborationMatrixDevice(
            gateway,
            registry,
            credentials,
            matrix,
            keys,
            providerKeys,
            providerToDevice,
            recipients,
            conversations,
            roomScopes,
            syncCursors,
            providerSync,
            providerRoomAccess,
            providerRoomHistory,
            providerRoomMessage,
            sessions,
            policies,
            companies,
            members,
            identities,
            journal,
            transactions,
            security,
            service,
            provider,
            clock,
            pushBindings,
            providerPush,
            nativePush,
            secretStorage,
        )

    @Bean
    fun refreshNativeCollaborationGateway(
        gateway: CollaborationGatewaySessionRepository,
        registry: CollaborationRegistryRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        RefreshNativeCollaborationGateway(
            gateway,
            registry,
            sessions,
            policies,
            companies,
            members,
            identities,
            journal,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun collaborationProviderSessionSource(
        sql: DSLContext
    ): CollaborationProviderSessionDataSource = PostgresCollaborationProviderSessionDataSource(sql)

    @Bean
    fun collaborationProviderSessions(
        source: CollaborationProviderSessionDataSource,
        crypto: IdentityTokenDataSource,
    ): CollaborationProviderSessionRepository =
        EncryptedCollaborationProviderSessionRepository(source, crypto)

    @Bean
    fun advanceNativeCollaborationProvisioning(
        registry: CollaborationRegistryRepository,
        provisioning: CollaborationProvisioningRepository,
        credentials: CollaborationProviderSessionRepository,
        matrix: CollaborationMatrixRepository,
        jobs: JobRepository,
        sessions: NativeSessionRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        service: CollaborationServicePolicy,
        provider: CollaborationMatrixPolicy,
        clock: Clock,
    ) =
        AdvanceNativeCollaborationProvisioning(
            registry,
            provisioning,
            credentials,
            matrix,
            jobs,
            sessions,
            policies,
            companies,
            members,
            identities,
            journal,
            transactions,
            security,
            service,
            provider,
            clock,
        )

    @Bean
    fun abortCollaborationProvisioning(
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortCollaborationProvisioning(jobs, journal, transactions)

    @Bean
    fun collaborationRegistrySource(sql: DSLContext): CollaborationRegistryDataSource =
        PostgresCollaborationRegistryDataSource(sql)

    @Bean
    fun listNativeCollaborationDevices(
        registry: CollaborationRegistryRepository,
        sessions: NativeSessionRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        service: CollaborationServicePolicy,
    ) = ListNativeCollaborationDevices(registry, sessions, identities, transactions, clock, service)

    @Bean
    fun collaborationRegistry(
        source: CollaborationRegistryDataSource
    ): CollaborationRegistryRepository = StoredCollaborationRegistryRepository(source)

    @Bean
    fun collaborationProvisioningSource(sql: DSLContext): CollaborationProvisioningDataSource =
        PostgresCollaborationProvisioningDataSource(sql)

    @Bean
    fun collaborationProvisioning(
        source: CollaborationProvisioningDataSource
    ): CollaborationProvisioningRepository = StoredCollaborationProvisioningRepository(source)

    @Bean
    fun registerNativeCollaborationDevice(
        registry: CollaborationRegistryRepository,
        provisioning: CollaborationProvisioningRepository,
        jobs: JobRepository,
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
            provisioning,
            jobs,
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
    fun getNativeCollaborationDeviceStatus(
        registry: CollaborationRegistryRepository,
        gateway: CollaborationGatewaySessionRepository,
        provisioning: CollaborationProvisioningRepository,
        jobs: JobRepository,
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
        GetNativeCollaborationDeviceStatus(
            registry,
            gateway,
            provisioning,
            jobs,
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

    @Bean
    fun retryNativeCollaborationProvisioning(
        registry: CollaborationRegistryRepository,
        provisioning: CollaborationProvisioningRepository,
        jobs: JobRepository,
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
        RetryNativeCollaborationProvisioning(
            registry,
            provisioning,
            jobs,
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
