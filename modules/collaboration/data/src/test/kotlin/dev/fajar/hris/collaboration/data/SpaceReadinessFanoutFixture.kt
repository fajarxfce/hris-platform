package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.FanOutCollaborationSpaceReadinessControl
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.postgresql.PostgreSQLContainer

/** Real, non-owner worker repositories share one transaction; only external delivery is a fake. */
internal class SpaceReadinessFanoutFixture(postgres: PostgreSQLContainer) {
    val e = SpaceKeyExchangeFixture(postgres)
    val sql = DSL.using(TransactionAwareDataSourceProxy(e.s.workerSource), SQLDialect.POSTGRES)
    val queue =
        StoredCollaborationSpaceReadinessQueueRepository(
            PostgresCollaborationSpaceReadinessQueueDataSource(sql)
        )
    val spaces = StoredCollaborationSpaceRepository(PostgresCollaborationSpaceDataSource(sql))
    val providers =
        EncryptedCollaborationSpaceProviderSessionRepository(
            PostgresCollaborationSpaceProviderSessionDataSource(sql),
            JceIdentityTokenDataSource(e.s.currentKeys),
        )
    private val noTokens =
        object : IdentityTokenDataSource {
            override fun generate(): String = error("No native issuance in readiness worker")

            override fun hash(token: String): String =
                error("No native token lookup in readiness worker")

            override fun encrypt(purpose: String, value: String): String =
                error("No native encryption in readiness worker")

            override fun decrypt(purpose: String, value: String): String =
                error("No native decryption in readiness worker")
        }
    val sessions = StoredNativeSessionRepository(PostgresNativeSessionDataSource(sql), noTokens)
    val identities =
        StoredIdentityRepository(
            PostgresIdentityDataSource(sql),
            object : PasswordDataSource {
                override fun hash(password: String): String? =
                    error("No password in readiness worker")

                override fun matches(password: String, hash: String): Boolean =
                    error("No password in readiness worker")
            },
            "unused-fixture-hash",
        )
    val deliveries = mutableListOf<CollaborationSpaceReadinessControl>()
    var outcome: () -> Result<Unit> = { Result.Success(Unit) }
    val transport =
        object : CollaborationSpaceMatrixReadinessRepository {
            override fun send(
                control: CollaborationSpaceReadinessControl,
                owner: CollaborationSpaceHistoryOwner,
                token: String,
                deadline: Instant,
            ): Result<Unit> {
                assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "Provider I/O must follow commit",
                )
                assertEquals(e.peer, control.accountId)
                assertEquals(e.peerDevice, control.deviceId)
                assertEquals(e.peerParent, control.nativeSessionId)
                assertEquals(e.s.space, control.spaceId)
                assertEquals(e.peerKeys.userId, owner.matrixUserId)
                assertEquals(e.peerKeys.deviceId, owner.matrixDeviceId)
                assertEquals("fixture-peer-live-token", token)
                assertTrue(deadline > now())
                assertTrue(deadline <= now().plusSeconds(20))
                deliveries += control
                return outcome()
            }
        }

    init {
        rotatePeer()
        e.base.clock.at = now()
        success(
            e.base
                .command(providers = e.providers)
                .execute(
                    e.peerActor,
                    e.peerParent,
                    0,
                    e.s.space,
                    UUID.randomUUID(),
                    e.peerDevice,
                    1,
                    null,
                    e.base.client,
                )
        )
        e.reserve()
        e.reserve(account = e.peer)
        e.bind()
        Flyway.configure().dataSource(e.s.source).target("140").load().migrate()
        success(
            e.s.tx {
                e.base.journal.record(
                    e.s.actor,
                    ChangeRecord(
                        "collaboration_space_device",
                        e.s.device,
                        "collaboration.space_device_keys_published",
                    ),
                )
            }
        )
    }

    fun rotatePeer(
        token: String = "fixture-peer-live-token",
        expiresAt: Instant = now().plusSeconds(300),
        entered: () -> Unit = {},
    ) {
        success(
            e.s.tx(e.peerActor) {
                entered()
                e.providers.metadata(e.s.space, e.peer, e.peerDevice).flatMap { metadata ->
                    val previous = requireNotNull(metadata)
                    e.providers.rotate(
                        CollaborationSpaceProviderSession(
                            previous.copy(
                                generation = previous.generation + 1,
                                updatedAt = now(),
                                expiresAt = expiresAt,
                            ),
                            token,
                        ),
                        previous.generation,
                    )
                }
            }
        )
    }

    fun command(
        queue: CollaborationSpaceReadinessQueueRepository = this.queue,
        spaces: CollaborationSpaceRepository = this.spaces,
        clock: Clock = Clock.systemUTC(),
        service: CollaborationServicePolicy = CollaborationServicePolicy(enabled = true),
    ) =
        FanOutCollaborationSpaceReadinessControl(
            queue,
            providers,
            transport,
            StoredCollaborationSpaceDeviceRepository(
                PostgresCollaborationSpaceDeviceDataSource(sql)
            ),
            e.s.repository(e.s.workerSource),
            StoredCollaborationSpaceKeyRepository(PostgresCollaborationSpaceKeyDataSource(sql)),
            StoredCollaborationSpaceIdentityRepository(
                PostgresCollaborationSpaceIdentityDataSource(sql)
            ),
            StoredCollaborationLegacyParentRepository(
                PostgresCollaborationLegacyParentDataSource(sql)
            ),
            StoredCollaborationLegacyRuntimeCompletionRepository(
                PostgresCollaborationLegacyRuntimeCompletionDataSource(sql)
            ),
            StoredCollaborationSpaceProvisioningRepository(
                PostgresCollaborationSpaceProvisioningDataSource(sql)
            ),
            spaces,
            sessions,
            identities,
            e.workerTransactions,
            service,
            CollaborationSpacePolicy(true, 5, 5),
            CollaborationMatrixPolicy(true, "matrix.example.test"),
            clock,
        )

    fun claim() = success(command().claim(UUID.randomUUID(), 1)).single()

    fun checkpoint(lease: CollaborationSpaceReadinessLease) =
        success(tx { queue.find(lease, false) })

    fun rows() =
        e.s.owner.queryForList(
            "select * from collaboration_space_readiness_deliveries order by channel"
        )

    fun <T> tx(body: () -> Result<T>) = e.workerTransactions.run(e.s.actor, body)

    fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
