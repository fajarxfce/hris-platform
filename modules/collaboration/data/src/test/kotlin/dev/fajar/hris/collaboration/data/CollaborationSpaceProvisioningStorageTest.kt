package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CancellationException
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceProvisioningStorageTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun typedQueueFencesEveryLeaseProofAndRestoresRetryOutcomeAndOwnedCancellation() {
        val db = Database()
        val f = db.Fixture(verifiedMfa = true)
        val request = f.request()
        assertNull(success(f.run { f.queue.metadata(db.space, f.account, f.device) }))
        success(f.run { f.queue.enqueue(request) })
        assertInstanceOf(Result.Failed::class.java, f.run { f.queue.enqueue(request) })
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = f.company),
            )) assertNull(success(f.run(actor) { f.queue.metadata(db.space, f.account, f.device) }))
        assertInstanceOf(Result.Failed::class.java, f.queue.claim(UUID.randomUUID(), 1, 60))
        val head = success(db.workerQueue.claim(UUID.randomUUID(), 1, 60)).single()
        assertEquals(request, head.request)
        var lease = requireNotNull(head.lease)
        val changedRequests =
            listOf(
                request.copy(spaceId = UUID.randomUUID()),
                request.copy(accountId = UUID.randomUUID()),
                request.copy(deviceId = UUID.randomUUID()),
                request.copy(nativeSessionId = UUID.randomUUID()),
                request.copy(revision = 1),
                request.copy(nativeSessionVersion = 1),
                request.copy(credentialVersion = 1),
                request.copy(authenticatedAt = request.authenticatedAt.plusNanos(1000)),
                request.copy(mfaVerifiedAt = request.authenticatedAt),
                request.copy(mfaVerifiedAt = null),
                request.copy(correlationId = UUID.randomUUID()),
                request.copy(client = request.client.copy(build = 29)),
                request.copy(requestedAt = request.requestedAt.plusNanos(1000)),
                request.copy(expiresAt = request.expiresAt.minusNanos(1000)),
            )
        val staleLeases =
            changedRequests.map { lease.copy(request = it) } +
                listOf(
                    lease.copy(attempt = 2),
                    lease.copy(ownerId = UUID.randomUUID()),
                    lease.copy(token = UUID.randomUUID()),
                    lease.copy(until = lease.until.plusNanos(1000)),
                )
        for (stale in staleLeases) assertEquals(Result.Success(false), db.workerQueue.finish(stale))
        assertEquals(
            head,
            success(f.run { f.queue.metadata(db.space, f.account, f.device, lock = true) }),
        )
        val deferredLease = lease
        assertTrue(success(db.workerQueue.defer(lease, 1)))
        val deferred =
            requireNotNull(success(f.run { f.queue.metadata(db.space, f.account, f.device) }))
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, deferred.state)
        assertEquals(1, deferred.attempts)
        assertNull(deferred.lease)
        assertFalse(success(db.workerQueue.finish(deferredLease)))
        Thread.sleep(1100)
        lease =
            requireNotNull(success(db.workerQueue.claim(UUID.randomUUID(), 1, 60)).single().lease)
        assertEquals(2, lease.attempt)
        assertFalse(success(db.workerQueue.finish(deferredLease)))
        assertTrue(
            success(
                db.workerQueue.finish(
                    lease,
                    CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE,
                )
            )
        )
        assertFalse(success(db.workerQueue.finish(lease)))
        val failed =
            requireNotNull(success(f.run { f.queue.metadata(db.space, f.account, f.device) }))
        assertEquals(CollaborationSpaceProvisioningState.FAILED, failed.state)
        assertEquals(
            CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE,
            failed.lastOutcome?.failure,
        )
        db.owner.update(
            "update native_sessions set version=version+1,rotated_at=clock_timestamp() where id=?",
            f.parent,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.queue.retry(f.request(revision = 1), 0) },
        )
        val fresh = f.request(revision = 1, nativeVersion = 1)
        success(f.run { f.queue.retry(fresh, 0) })
        val waiting =
            requireNotNull(success(f.run { f.queue.metadata(db.space, f.account, f.device) }))
        assertEquals(fresh, waiting.request)
        assertEquals(head.createdAt, waiting.createdAt)
        assertEquals(failed.lastOutcome, waiting.lastOutcome)
        assertEquals(0, waiting.attempts)
        assertFalse(success(db.workerQueue.finish(lease)))
        assertInstanceOf(Result.Failed::class.java, f.run { f.queue.retry(fresh, 0) })

        val removed = db.Fixture()
        success(removed.run { removed.queue.enqueue(removed.request()) })
        db.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            removed.account,
        )
        db.owner.update("delete from native_sessions where id=?", removed.parent)
        assertNotNull(
            success(
                removed.run { removed.queue.metadata(db.space, removed.account, removed.device) }
            )
        )
        assertTrue(
            success(
                removed.run { removed.queue.cancel(db.space, removed.account, removed.device, 0) }
            )
        )
        val cancelled =
            requireNotNull(
                success(
                    removed.run {
                        removed.queue.metadata(db.space, removed.account, removed.device)
                    }
                )
            )
        assertEquals(CollaborationSpaceProvisioningState.CANCELLED, cancelled.state)
        assertFalse(
            success(
                removed.run { removed.queue.cancel(db.space, removed.account, removed.device, 0) }
            )
        )
        assertInstanceOf(
            Result.Failed::class.java,
            removed.run { removed.queue.retry(removed.request(revision = 1), 0) },
        )

        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/queue-adapter.dump",
        )
        command("createdb", "-U", postgres.username, "queue_adapter_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "queue_adapter_restored",
            "--single-transaction",
            "--exit-on-error",
            "/tmp/queue-adapter.dump",
        )
        val restoredSource = runtime("queue_adapter_restored", "space_queue_fixture")
        val restored = queue(restoredSource)
        val transactions = tx(restoredSource)
        assertEquals(
            waiting,
            success(transactions.run(f.actor) { restored.metadata(db.space, f.account, f.device) }),
        )
        assertEquals(
            cancelled,
            success(
                transactions.run(removed.actor) {
                    restored.metadata(db.space, removed.account, removed.device)
                }
            ),
        )
        assertNull(
            success(
                transactions.run(removed.actor) { restored.metadata(db.space, f.account, f.device) }
            )
        )
        val restoredWorker = queue(runtime("queue_adapter_restored", "space_queue_worker"))
        val claimed = success(restoredWorker.claim(UUID.randomUUID(), 1, 60)).single()
        assertEquals(fresh, claimed.request)
        assertEquals(1, claimed.attempts)
        assertNotNull(claimed.lease)
        assertEquals(waiting, success(f.run { f.queue.metadata(db.space, f.account, f.device) }))
    }

    @Test
    fun failedCancelledAndInterruptedPublicationRollBackDeviceCredentialAndQueueTogether() {
        val db = Database()
        val f = db.Fixture()
        success(f.run { f.queue.enqueue(f.request()) })
        val head = success(db.workerQueue.claim(UUID.randomUUID(), 1, 60)).single()
        val lease = requireNotNull(head.lease)
        val initial =
            requireNotNull(success(f.work { db.registry.metadata(db.space, f.account, f.device) }))
        val at = now()
        val candidate =
            CollaborationSpaceProviderSession(
                CollaborationSpaceProviderState(
                    db.space,
                    f.account,
                    f.device,
                    f.parent,
                    f.user,
                    "HRISG_${compact(f.device)}",
                    0,
                    at,
                    at,
                    at.plusSeconds(300),
                ),
                "fictional-queue-publication-token",
            )
        val active =
            initial.copy(state = CollaborationDeviceState.ACTIVE, version = 1, updatedAt = at)
        fun publish(): Result<Unit> {
            assertEquals(
                initial,
                success(db.registry.metadata(db.space, f.account, f.device, lock = true)),
            )
            assertEquals(
                lease,
                success(db.workerQueue.metadata(db.space, f.account, f.device, lock = true))?.lease,
            )
            success(db.credentials.create(candidate))
            success(db.registry.replace(active, 0))
            assertTrue(success(db.workerQueue.finish(lease)))
            assertEquals(
                CollaborationSpaceProvisioningState.SUCCEEDED,
                success(db.workerQueue.metadata(db.space, f.account, f.device))?.state,
            )
            return Result.Success(Unit)
        }
        fun unchanged() {
            assertEquals(
                initial,
                success(f.work { db.registry.metadata(db.space, f.account, f.device) }),
            )
            assertNull(success(f.work { db.credentials.metadata(db.space, f.account, f.device) }))
            assertEquals(head, success(f.run { f.queue.metadata(db.space, f.account, f.device) }))
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.work {
                success(publish())
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_failure"))
            },
        )
        unchanged()
        for (error in listOf(CancellationException("fixture"), InterruptedException("fixture"))) {
            assertThrows(error.javaClass) {
                f.work<Unit> {
                    success(publish())
                    throw error
                }
            }
            unchanged()
        }
        success(f.work { publish() })
        assertEquals(
            active,
            success(f.work { db.registry.metadata(db.space, f.account, f.device) }),
        )
        assertEquals(candidate, success(f.work { db.credentials.credential(candidate.state) }))
        assertEquals(
            CollaborationSpaceProvisioningState.SUCCEEDED,
            success(f.run { f.queue.metadata(db.space, f.account, f.device) })?.state,
        )
    }

    private inner class Database {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val owner = JdbcTemplate(source)
        val runtimeSource = runtime(postgres.databaseName, "space_queue_fixture")
        val workerSource = runtime(postgres.databaseName, "space_queue_worker")
        val workerQueue = queue(workerSource)
        val registry =
            StoredCollaborationSpaceDeviceRepository(
                PostgresCollaborationSpaceDeviceDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(workerSource), SQLDialect.POSTGRES)
                )
            )
        val credentials =
            EncryptedCollaborationSpaceProviderSessionRepository(
                PostgresCollaborationSpaceProviderSessionDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(workerSource), SQLDialect.POSTGRES)
                ),
                JceIdentityTokenDataSource(
                    parseIdentityKeyring(
                        "fixture",
                        "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 31 }),
                    )
                ),
            )
        val space: UUID

        init {
            Flyway.configure().dataSource(source).target("122").load().migrate()
            space =
                requireNotNull(
                    owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
                )
            for (role in listOf("space_queue_fixture", "space_queue_worker")) {
                owner.execute("create role $role login password 'isolated-fixture-only'")
                owner.execute("grant usage on schema public to $role")
                owner.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
            owner.execute("grant hris_worker_capability to space_queue_worker")
        }

        inner class Fixture(verifiedMfa: Boolean = false) {
            val account = UUID.randomUUID()
            val company = UUID.randomUUID()
            val parent = UUID.randomUUID()
            val device = UUID.randomUUID()
            val at = now()
            val actor =
                Actor(
                    account,
                    null,
                    emptySet(),
                    at.minusSeconds(60),
                    UUID.randomUUID(),
                    mfaVerifiedAt = if (verifiedMfa) at.minusSeconds(30) else null,
                    credentialVersion = 0,
                )
            val user = "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test"
            val queue = queue(runtimeSource)
            val transactions = tx(runtimeSource)
            val workerTransactions = tx(workerSource)

            init {
                owner.update(
                    "insert into companies(id,code,name,timezone) values(?,?,'Fictional','UTC')",
                    company,
                    compact(company).take(20),
                )
                owner.update(
                    "insert into accounts(id,email,display_name) values(?,?,'Fictional')",
                    account,
                    "$account@example.test",
                )
                owner.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    company,
                    account,
                )
                owner.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    company,
                    account,
                )
                owner.update(
                    "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at,mfa_verified_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)",
                    parent,
                    account,
                    compact(parent).repeat(2),
                    actor.authenticatedAt.toString(),
                    at.plusSeconds(3600).toString(),
                    at.plusSeconds(900).toString(),
                    at.minusSeconds(60).toString(),
                    at.minusSeconds(30).toString(),
                    actor.mfaVerifiedAt?.toString(),
                )
                owner.update(
                    "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                    space,
                    account,
                    user,
                    at.toString(),
                )
                owner.update(
                    "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,'SPACE',?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                    space,
                    device,
                    account,
                    user,
                    "HRISG_${compact(device)}",
                    parent,
                    at.toString(),
                    at.toString(),
                )
                owner.update(
                    "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                    space,
                    device,
                    account,
                    parent,
                    at.toString(),
                )
            }

            fun request(
                revision: Long = 0,
                nativeVersion: Long = 0,
            ): CollaborationSpaceProvisioningRequest {
                val requested = now()
                return CollaborationSpaceProvisioningRequest(
                    space,
                    account,
                    device,
                    parent,
                    revision,
                    nativeVersion,
                    0,
                    actor.authenticatedAt,
                    actor.mfaVerifiedAt,
                    UUID.randomUUID(),
                    ClientVersion(ClientPlatform.ANDROID, 28),
                    requested,
                    requested.plusSeconds(300),
                )
            }

            fun <T> run(current: Actor = actor, body: () -> Result<T>): Result<T> =
                transactions.run(current, body)

            fun <T> work(body: () -> Result<T>): Result<T> = workerTransactions.run(actor, body)
        }
    }

    private fun runtime(database: String, role: String) =
        DriverManagerDataSource(
            postgres.jdbcUrl.substringBeforeLast('/') + "/" + database,
            role,
            "isolated-fixture-only",
        )

    private fun tx(source: DriverManagerDataSource) =
        PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source))

    private fun queue(source: DriverManagerDataSource) =
        StoredCollaborationSpaceProvisioningRepository(
            PostgresCollaborationSpaceProvisioningDataSource(
                DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
            )
        )

    private fun command(vararg args: String) {
        val result = postgres.execInContainer(*args)
        assertEquals(0, result.exitCode, result.stderr)
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
