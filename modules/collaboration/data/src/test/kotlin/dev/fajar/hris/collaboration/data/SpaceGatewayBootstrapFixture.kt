package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.BootstrapNativeCollaborationSpaceGateway
import dev.fajar.hris.collaboration.domain.usecases.RefreshNativeCollaborationSpaceGateway
import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.database.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.util.UUID
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper

internal class SpaceGatewayBootstrapFixture(postgres: PostgreSQLContainer) {
    val storage = SpaceGatewayPostgresFixture(postgres, ageSeconds = 120).also { it.migrate() }
    val clock = SpaceGatewayClock(storage.at.plusSeconds(10))
    val client = ClientVersion(ClientPlatform.ANDROID, 5)
    val sql = DSL.using(TransactionAwareDataSourceProxy(storage.runtimeSource), SQLDialect.POSTGRES)
    val operations =
        PostgresOperationRepository(PostgresOperationReceiptDataSource(sql), ObjectMapper())
    val journal =
        PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, ObjectMapper()))
    val spaces = StoredCollaborationSpaceRepository(PostgresCollaborationSpaceDataSource(sql))
    val selections =
        StoredCollaborationSpaceIdentityRepository(
            PostgresCollaborationSpaceIdentityDataSource(sql)
        )
    val registry =
        StoredCollaborationSpaceDeviceRepository(PostgresCollaborationSpaceDeviceDataSource(sql))
    val legacy =
        StoredCollaborationLegacyParentRepository(PostgresCollaborationLegacyParentDataSource(sql))
    val completions =
        StoredCollaborationLegacyRuntimeCompletionRepository(
            PostgresCollaborationLegacyRuntimeCompletionDataSource(sql)
        )
    val provisioning =
        StoredCollaborationSpaceProvisioningRepository(
            PostgresCollaborationSpaceProvisioningDataSource(sql)
        )
    private val noNativeTokens =
        object : IdentityTokenDataSource {
            override fun generate(): String =
                error("No native credential issuance in gateway bootstrap")

            override fun hash(token: String): String =
                error("No native credential lookup in gateway bootstrap")

            override fun encrypt(purpose: String, value: String): String =
                error("No native/provider encryption in gateway bootstrap")

            override fun decrypt(purpose: String, value: String): String =
                error("No native/provider decryption in gateway bootstrap")
        }
    val providers =
        EncryptedCollaborationSpaceProviderSessionRepository(
            PostgresCollaborationSpaceProviderSessionDataSource(sql),
            noNativeTokens,
        )
    val sessions =
        StoredNativeSessionRepository(PostgresNativeSessionDataSource(sql), noNativeTokens)
    val identities =
        StoredIdentityRepository(
            PostgresIdentityDataSource(sql),
            object : PasswordDataSource {
                override fun hash(password: String): String? =
                    error("No password acquisition in gateway bootstrap")

                override fun matches(password: String, hash: String): Boolean =
                    error("No password acquisition in gateway bootstrap")
            },
            "unused-fixture-hash",
        )

    fun command(
        gateway: CollaborationSpaceGatewaySessionRepository = storage.repo,
        operations: OperationRepository = this.operations,
        journal: ChangeJournalRepository = this.journal,
        providers: CollaborationSpaceProviderSessionRepository = this.providers,
        spaces: CollaborationSpaceRepository = this.spaces,
        registry: CollaborationSpaceDeviceRepository = this.registry,
        policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 5, 5),
    ) =
        BootstrapNativeCollaborationSpaceGateway(
            gateway,
            registry,
            providers,
            selections,
            legacy,
            completions,
            provisioning,
            spaces,
            sessions,
            identities,
            operations,
            journal,
            storage.transactions,
            IdentitySecurityPolicy(),
            policy,
            CollaborationMatrixPolicy(true, "matrix.example.test"),
            clock,
        )

    fun bootstrap(
        operation: UUID = UUID.randomUUID(),
        expectedGatewayVersion: Long? = null,
        request: Actor = storage.actor,
        nativeVersion: Long = 0,
        build: ClientVersion? = client,
        command: BootstrapNativeCollaborationSpaceGateway = command(),
    ) =
        bootstrapEnvelope(operation, expectedGatewayVersion, request, nativeVersion, build, command)
            .map { it.tokens }

    fun bootstrapEnvelope(
        operation: UUID = UUID.randomUUID(),
        expectedGatewayVersion: Long? = null,
        request: Actor = storage.actor,
        nativeVersion: Long = 0,
        build: ClientVersion? = client,
        command: BootstrapNativeCollaborationSpaceGateway = command(),
    ) =
        command.execute(
            request,
            storage.parent,
            nativeVersion,
            storage.space,
            operation,
            storage.device,
            1,
            expectedGatewayVersion,
            build,
        )

    fun refresh(
        token: String,
        journal: ChangeJournalRepository = this.journal,
        providers: CollaborationSpaceProviderSessionRepository = this.providers,
        spaces: CollaborationSpaceRepository = this.spaces,
        gateway: CollaborationSpaceGatewaySessionRepository = storage.repo,
        policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 5, 5),
    ) =
        RefreshNativeCollaborationSpaceGateway(
                gateway,
                registry,
                providers,
                selections,
                legacy,
                completions,
                provisioning,
                spaces,
                sessions,
                identities,
                journal,
                storage.transactions,
                IdentitySecurityPolicy(),
                policy,
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
            )
            .execute(token, UUID.randomUUID())

    fun counts() =
        listOf(
                "collaboration_space_gateway_sessions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith {
                storage.owner.queryForObject("select count(*) from $it", Int::class.java)!!
            }
}

internal class SpaceGatewayClock(var at: Instant) : Clock() {
    override fun instant() = at

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}
