package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationLegacyRuntimeDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyRuntimeRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
class CollaborationLegacyRuntimeStorageTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun exactLeaseGuardSerializesPublicationObservationWithFinallyCleanup() {
        with(Fixture()) {
            migrate()
            success(legacy { repository.ensure(key, now()) })
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            val entered = CountDownLatch(1)
            val leave = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val observation =
                    pool.submit<CollaborationLegacyRuntimeLease> {
                        requireNotNull(
                            success(
                                root {
                                    repository.find(key, true).flatMap {
                                        repository.lease(key, lease.slot, true).map { value ->
                                            entered.countDown()
                                            check(leave.await(5, TimeUnit.SECONDS))
                                            value
                                        }
                                    }
                                }
                            )
                        )
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertInstanceOf(
                    Result.Failed::class.java,
                    run {
                        runtime.queryForList(
                            "select * from collaboration_legacy_runtime_leases where token=? for update nowait",
                            lease.token,
                        )
                    },
                )
                val cleanup = pool.submit<Boolean> { success(root { repository.release(lease) }) }
                assertFalse(cleanup.isDone)
                leave.countDown()
                assertEquals(lease, observation.get(10, TimeUnit.SECONDS))
                assertTrue(cleanup.get(10, TimeUnit.SECONDS))
                assertNotNull(
                    success(root { repository.lease(key, lease.slot, false) })?.releasedAt
                )
            } finally {
                leave.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun typedOwnershipAndEveryReleaseCoordinateFenceAReusedSlotAcrossFreeze() {
        with(Fixture()) {
            migrate()
            assertNull(success(root { repository.find(key, false) }))
            val initial = success(legacy { repository.ensure(key, now()) })
            assertEquals(initial, success(legacy { repository.ensure(key, now()) }))
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            val wrong =
                listOf(
                    lease.copy(owner = key.copy(companyId = UUID.randomUUID())),
                    lease.copy(owner = key.copy(deviceId = UUID.randomUUID())),
                    lease.copy(owner = key.copy(accountId = UUID.randomUUID())),
                    lease.copy(owner = key.copy(nativeSessionId = UUID.randomUUID())),
                    lease.copy(slot = 1),
                    lease.copy(token = UUID.randomUUID()),
                    lease.copy(startedAt = lease.startedAt.plusNanos(1000)),
                    lease.copy(expiresAt = lease.expiresAt.minusNanos(1000)),
                )
            for (stale in wrong) assertFalse(success(root { repository.release(stale) }))
            assertEquals(listOf(lease), success(root { repository.slots(key) }))
            assertTrue(success(root { repository.release(lease) }))
            val replacement =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertEquals(lease.slot, replacement.slot)
            assertNotEquals(lease.token, replacement.token)
            assertFalse(success(root { repository.release(lease) }))
            val target = target()
            assertFalse(
                success(
                    root {
                        repository.freeze(
                            key.copy(nativeSessionId = UUID.randomUUID()),
                            target,
                            0,
                            now(),
                        )
                    }
                )
            )
            assertTrue(success(root { repository.freeze(key, target, 0, now()) }))
            val frozen = requireNotNull(success(root { repository.find(key, true) }))
            assertEquals(CollaborationLegacyRuntimePhase.DRAINING, frozen.phase)
            assertEquals(target, frozen.target)
            assertEquals(frozen, success(legacy { repository.ensure(key, now()) }))
            assertNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertFalse(success(root { repository.freeze(key, target, 0, now()) }))
            assertTrue(success(root { repository.release(replacement) }))
            assertFalse(success(root { repository.release(replacement) }))
            assertNull(
                success(
                    transactions.run(actor.copy(accountId = peer)) { repository.find(key, false) }
                )
            )
            assertTrue(
                success(transactions.run(actor.copy(accountId = peer)) { repository.slots(key) })
                    .isEmpty()
            )
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor.copy(companyId = employer)) {
                    repository.claim(key, now().plusSeconds(30))
                },
            )
        }
    }

    @Test
    fun callerRollbackCancellationAndInterruptionPreserveTheHeadAndLiveLease() {
        with(Fixture()) {
            migrate()
            val initial = success(legacy { repository.ensure(key, now()) })
            val failed = Result.Failed(Failure(FailureKind.CONFLICT, "fixture_failure"))
            assertEquals(
                failed,
                legacy { repository.claim(key, now().plusSeconds(30)).flatMap { failed } },
            )
            assertTrue(success(root { repository.slots(key) }).isEmpty())
            for (failure in
                listOf(
                    CancellationException("owned fixture"),
                    InterruptedException("owned fixture"),
                )) {
                assertThrows(failure.javaClass) {
                    legacy<Unit> {
                        repository.claim(key, now().plusSeconds(30)).flatMap { throw failure }
                    }
                }
                assertTrue(success(root { repository.slots(key) }).isEmpty())
            }
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertEquals(failed, root { repository.release(lease).flatMap { failed } })
            assertEquals(listOf(lease), success(root { repository.slots(key) }))
            val target = target()
            assertEquals(
                failed,
                root { repository.freeze(key, target, 0, now()).flatMap { failed } },
            )
            assertEquals(initial, success(root { repository.find(key, false) }))
            assertTrue(success(root { repository.release(lease) }))
        }
    }

    @Test
    fun ownedMetadataAndExactCleanupRemainAvailableAfterNativeParentPurge() {
        with(Fixture()) {
            migrate()
            success(legacy { repository.ensure(key, now()) })
            val lease =
                requireNotNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            val target = target()
            success(root { repository.freeze(key, target, 0, now()) })
            val retained = success(root { repository.find(key, false) })
            sql.update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                account,
            )
            sql.update("delete from native_sessions where id=?", parent)
            assertEquals(retained, success(root { repository.find(key, false) }))
            assertEquals(listOf(lease), success(root { repository.slots(key) }))
            assertTrue(success(root { repository.release(lease) }))
            assertFalse(success(root { repository.release(lease) }))
            assertNull(success(legacy { repository.claim(key, now().plusSeconds(30)) }))
            assertInstanceOf(Result.Failed::class.java, legacy { repository.ensure(key, now()) })
            assertEquals(retained, success(root { repository.find(key, false) }))
            val released = success(root { repository.slots(key) }).single()
            assertEquals(lease, released.copy(releasedAt = null))
            assertNotNull(released.releasedAt)
        }
    }

    private inner class Fixture {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val sql = JdbcTemplate(source)
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "legacy_runtime_fixture",
                "isolated-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val repository =
            StoredCollaborationLegacyRuntimeRepository(
                PostgresCollaborationLegacyRuntimeDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtimeSource), SQLDialect.POSTGRES)
                )
            )
        val company = UUID.randomUUID()
        val employer = UUID.randomUUID()
        val account = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val legacyDevice = UUID.randomUUID()
        val at = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val actor =
            Actor(
                account,
                null,
                emptySet(),
                at.minusSeconds(120),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val space: UUID
        val parent: UUID
        val fingerprint = "a".repeat(64)
        val legacyUser = "@hris_${compact(company)}_${compact(account)}:matrix.example.test"

        init {
            Flyway.configure().dataSource(source).target("119").load().migrate()
            space =
                requireNotNull(
                    sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
                )
            for ((index, id) in listOf(company, employer).withIndex()) sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                id,
                "SPACEDEV$index",
                "Fictional company $index",
            )
            for ((index, id) in listOf(account, peer).withIndex()) {
                sql.update(
                    "insert into accounts(id,email,display_name) values(?,?,?)",
                    id,
                    "space-device-$index@example.test",
                    "Fictional employee $index",
                )
                sql.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    employer,
                    id,
                )
                sql.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    employer,
                    id,
                )
            }
            sql.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
            parent = addParent()
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                legacyDevice,
                account,
                at.minusSeconds(60).toString(),
                at.minusSeconds(60).toString(),
            )
            sql.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                company,
                legacyDevice,
                account,
                parent,
                at.minusSeconds(30).toString(),
            )
            sql.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-real-token',?::timestamptz,?::timestamptz,?::timestamptz)",
                company,
                legacyDevice,
                account,
                legacyUser,
                "HRIS_${compact(legacyDevice)}",
                at.minusSeconds(30).toString(),
                at.minusSeconds(30).toString(),
                at.plusSeconds(270).toString(),
            )
            sql.update(
                "update collaboration_devices set state='ACTIVE',version=1,updated_at=?::timestamptz where company_id=? and id=?",
                at.minusSeconds(30).toString(),
                company,
                legacyDevice,
            )
            sql.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,?::timestamptz)",
                company,
                legacyDevice,
                account,
                fingerprint,
                at.minusSeconds(20).toString(),
            )
            sql.update(
                "update collaboration_device_key_publications set version=1,published_at=?::timestamptz where company_id=? and device_id=?",
                at.minusSeconds(19).toString(),
                company,
                legacyDevice,
            )
            sql.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,legacy_company_id,legacy_device_id,created_at) values(?,?,'matrix.example.test',?,'LEGACY',?,?,?::timestamptz)",
                space,
                account,
                legacyUser,
                company,
                legacyDevice,
                at.minusSeconds(10).toString(),
            )
        }

        fun addParent(owner: UUID = account): UUID =
            UUID.randomUUID().also {
                sql.update(
                    "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                    it,
                    owner,
                    compact(it).repeat(2),
                    actor.authenticatedAt.toString(),
                    at.plusSeconds(3600).toString(),
                    at.plusSeconds(900).toString(),
                    at.minusSeconds(60).toString(),
                    at.minusSeconds(30).toString(),
                )
            }

        fun migrate() {
            Flyway.configure().dataSource(source).target("123").load().migrate()
            sql.execute("create role legacy_runtime_fixture login password 'isolated-fixture-only'")
            sql.execute("grant usage on schema public to legacy_runtime_fixture")
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to legacy_runtime_fixture"
            )
        }

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun insert(
            id: UUID,
            origin: String = "LEGACY",
            selectedSpace: UUID = space,
            user: String = legacyUser,
            oldDevice: UUID = legacyDevice,
            key: String = fingerprint,
            session: UUID = parent,
            platform: String = "ANDROID",
            providerDevice: String =
                if (origin == "LEGACY") "HRIS_${compact(oldDevice)}" else "HRISG_${compact(id)}",
        ): Int =
            runtime.update(
                "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,legacy_company_id,legacy_device_id,legacy_key_fingerprint,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,'Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                selectedSpace,
                id,
                account,
                user,
                providerDevice,
                origin,
                if (origin == "LEGACY") company else null,
                if (origin == "LEGACY") oldDevice else null,
                if (origin == "LEGACY") key else null,
                session,
                platform,
                at.toString(),
                at.toString(),
            )

        fun bind(device: UUID, session: UUID = parent): Int =
            runtime.update(
                "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                space,
                device,
                account,
                session,
                at.toString(),
            )

        val key
            get() = CollaborationLegacyRuntimeOwner(company, legacyDevice, account, parent)

        fun <T> legacy(body: () -> Result<T>): Result<T> =
            transactions.run(actor.copy(companyId = company), body)

        fun <T> root(body: () -> Result<T>): Result<T> = transactions.run(actor, body)

        fun target(): CollaborationLegacyRuntimeTarget =
            UUID.randomUUID().let { id ->
                success(run { insert(id) })
                success(run { bind(id) })
                CollaborationLegacyRuntimeTarget(space, id)
            }
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
