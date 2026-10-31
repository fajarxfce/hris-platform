package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationDeviceKeyDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationDeviceKeyRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationDeviceKeyPublication
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationDeviceKeyStorageTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
        lateinit var owner: JdbcTemplate

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            owner = JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("111").load().migrate()
            val account = UUID.randomUUID()
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Pre-upgrade key fixture')",
                account,
                "$account@example.test",
            )
            val before =
                owner.queryForList(
                    "select row_to_json(a)::text as snapshot from accounts a order by id"
                )
            Flyway.configure().dataSource(source).target("112").load().migrate()
            assertEquals(
                before,
                owner.queryForList(
                    "select row_to_json(a)::text as snapshot from accounts a order by id"
                ),
            )
            owner.execute("create role device_key_fixture login password 'device-key-fixture-only'")
            owner.execute("grant usage on schema public to device_key_fixture")
            owner.execute(
                "grant select,insert,update,delete on all tables in schema public to device_key_fixture"
            )
        }
    }

    @Test
    fun retainedIdentityHasOwnCompanyAccountScopeAndOnePublicationTransition() {
        val f = Fixture()
        val p = f.publication()
        assertEquals(Result.Success(Unit), f.run { f.repository.reserve(p) })
        assertEquals(Result.Success(p), f.read())
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = UUID.randomUUID()),
            )) {
            assertEquals(Result.Success(null), f.read(actor))
            assertInstanceOf(
                Result.Failed::class.java,
                f.run(actor) { f.repository.publish(p, f.now.plusSeconds(1)) },
            )
        }
        assertEquals(
            0,
            f.runtime.queryForObject(
                "select count(*) from collaboration_device_key_publications",
                Int::class.java,
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.repository.reserve(p.copy(fingerprint = "b".repeat(64))) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run {
                f.repository.publish(p.copy(fingerprint = "b".repeat(64)), f.now.plusSeconds(1))
            },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.repository.publish(p, f.now.minusSeconds(1)) },
        )
        for (assignment in
            listOf(
                "fingerprint=repeat('b',64)",
                "account_id=gen_random_uuid()",
                "reserved_at=reserved_at+interval '1 second'",
                "version=1",
            )) {
            assertThrows(DataAccessException::class.java) {
                owner.update(
                    "update collaboration_device_key_publications set $assignment where company_id=?",
                    f.company,
                )
            }
        }
        val at = f.now.plusSeconds(1)
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(p, at) })
        assertEquals(Result.Success(p.copy(version = 1, publishedAt = at)), f.read())
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.repository.publish(p, at.plusSeconds(1)) },
        )
        assertThrows(DataAccessException::class.java) {
            owner.update(
                "update collaboration_device_key_publications set published_at=published_at+interval '1 second' where company_id=?",
                f.company,
            )
        }
        owner.update("delete from native_sessions where id=?", f.parent)
        owner.update(
            "update collaboration_devices set state='REVOKED',version=2,revoked_at=updated_at where company_id=?",
            f.company,
        )
        assertEquals(Result.Success(p.copy(version = 1, publishedAt = at)), f.read())
        assertEquals(
            Result.Success(0),
            f.run {
                Result.Success(
                    f.runtime.update(
                        "delete from collaboration_device_key_publications where company_id=?",
                        f.company,
                    )
                )
            },
        )
        assertThrows(DataAccessException::class.java) {
            owner.update(
                "delete from collaboration_device_key_publications where company_id=?",
                f.company,
            )
        }
    }

    @Test
    fun reservationRejectsInactiveMismatchedAndAlreadyPublishedDevices() {
        val f = Fixture()
        val p = f.publication()
        for (invalid in
            listOf(
                p.copy(accountId = UUID.randomUUID()),
                p.copy(companyId = UUID.randomUUID()),
                p.copy(deviceId = UUID.randomUUID()),
                p.copy(fingerprint = "A".repeat(64)),
                p.copy(fingerprint = "a".repeat(63)),
                p.copy(version = 1, publishedAt = f.now),
                p.copy(reservedAt = f.now.minusSeconds(1)),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run { f.repository.reserve(invalid) })
        }
        val pending = f.device(active = false)
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.repository.reserve(p.copy(deviceId = pending)) },
        )
        assertEquals(Result.Success(Unit), f.run { f.repository.reserve(p) })
        owner.update(
            "update collaboration_devices set state='REVOKED',version=2,revoked_at=updated_at where company_id=? and id=?",
            f.company,
            f.device,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.repository.publish(p, f.now.plusSeconds(1)) },
        )
        assertEquals(Result.Success(p), f.read())
    }

    @Test
    fun competingReservationsWaitForOwnedGuardAndRetainFirstIdentity() {
        val f = Fixture()
        val first = f.publication()
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val a =
                pool.submit<Result<Unit>> {
                    f.run {
                        f.repository.lock(f.company, f.device).flatMap {
                            locked.countDown()
                            assertTrue(release.await(3, TimeUnit.SECONDS))
                            f.repository.reserve(first)
                        }
                    }
                }
            assertTrue(locked.await(3, TimeUnit.SECONDS))
            val b =
                pool.submit<Result<CollaborationDeviceKeyPublication?>> {
                    f.run {
                        started.countDown()
                        f.repository.lock(f.company, f.device).flatMap {
                            f.repository.find(f.company, f.account, f.device)
                        }
                    }
                }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            assertFalse(b.isDone)
            release.countDown()
            assertEquals(Result.Success(Unit), a.get(5, TimeUnit.SECONDS))
            assertEquals(Result.Success(first), b.get(5, TimeUnit.SECONDS))
            // A competing request cannot reserve a different identity after it acquires the guard.
            assertInstanceOf(
                Result.Failed::class.java,
                f.run { f.repository.reserve(first.copy(fingerprint = "b".repeat(64))) },
            )
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedResultAndInterruptionRollBackButUncertainPublicationRetainsReservation() {
        val f = Fixture()
        val p = f.publication()
        val failed = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_late_admission"))
        assertEquals(failed, f.run { f.repository.reserve(p).flatMap<Unit, Unit> { failed } })
        assertEquals(Result.Success(null), f.read())
        assertEquals(Result.Success(Unit), f.run { f.repository.reserve(p) })
        assertEquals(
            failed,
            f.run { f.repository.publish(p, f.now.plusSeconds(1)).flatMap<Unit, Unit> { failed } },
        )
        try {
            assertThrows(InterruptedException::class.java) {
                f.run {
                    f.repository.publish(p, f.now.plusSeconds(1)).flatMap<Unit, Unit> {
                        throw InterruptedException("fixture cancellation")
                    }
                }
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(Result.Success(p), f.read())
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(p, f.now.plusSeconds(2)) })
    }

    @Test
    fun realDumpRestoreRetainsPendingAndPublishedIdentitiesWithRlsAndMutationFences() {
        val f = Fixture()
        val p = f.publication()
        val second = f.publication().copy(deviceId = f.device())
        assertEquals(Result.Success(Unit), f.run { f.repository.reserve(p) })
        assertEquals(Result.Success(Unit), f.run { f.repository.reserve(second) })
        val at = f.now.plusSeconds(1)
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(p, at) })
        fun command(vararg args: String) {
            val result = postgres.execInContainer(*args)
            assertEquals(0, result.exitCode, result.stderr)
        }
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/device-keys.dump",
        )
        command("createdb", "-U", postgres.username, "device_keys_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "device_keys_restored",
            "--exit-on-error",
            "/tmp/device-keys.dump",
        )
        val url =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/device_keys_restored"
        val source = DriverManagerDataSource(url, "device_key_fixture", "device-key-fixture-only")
        val runtime = JdbcTemplate(source)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), runtime)
        val repository =
            StoredCollaborationDeviceKeyRepository(
                PostgresCollaborationDeviceKeyDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
                )
            )
        assertEquals(
            Result.Success(p.copy(version = 1, publishedAt = at)),
            tx.run(f.actor) { repository.find(f.company, f.account, f.device) },
        )
        assertEquals(
            Result.Success(second),
            tx.run(f.actor) { repository.find(f.company, f.account, second.deviceId) },
        )
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = UUID.randomUUID()),
            )) {
            assertEquals(
                Result.Success(null),
                tx.run(actor) { repository.find(f.company, f.account, f.device) },
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            tx.run(f.actor) { repository.publish(second.copy(fingerprint = "b".repeat(64)), at) },
        )
        assertEquals(Result.Success(Unit), tx.run(f.actor) { repository.publish(second, at) })
        val restoredOwner =
            JdbcTemplate(DriverManagerDataSource(url, postgres.username, postgres.password))
        assertThrows(DataAccessException::class.java) {
            restoredOwner.update(
                "delete from collaboration_device_key_publications where company_id=?",
                f.company,
            )
        }
        assertEquals(
            0,
            runtime.queryForObject(
                "select count(*) from collaboration_device_key_publications",
                Int::class.java,
            ),
        )
    }

    private class Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val now: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val actor = Actor(account, company, emptySet(), now, UUID.randomUUID())
        private val source =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "device_key_fixture",
                "device-key-fixture-only",
            )
        val runtime = JdbcTemplate(source)
        private val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), runtime)
        val repository =
            StoredCollaborationDeviceKeyRepository(
                PostgresCollaborationDeviceKeyDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
                )
            )
        val device: UUID

        init {
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional key company','UTC')",
                company,
                company.toString().replace("-", ""),
            )
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional key participant')",
                account,
                "$account@example.test",
            )
            val hash = UUID.randomUUID().toString().replace("-", "").repeat(2)
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_hash,access_expires_at,credential_version) values(?,?,?,now(),now()+interval '1 day',?,now()+interval '15 minutes',0)",
                parent,
                account,
                hash,
                hash,
            )
            device = device()
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?)",
                company,
                device,
                account,
                parent,
                java.sql.Timestamp.from(now),
            )
        }

        fun device(active: Boolean = true): UUID {
            val id = UUID.randomUUID()
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional key device','PROVISIONING',0,?,?)",
                company,
                id,
                account,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
            )
            if (active)
                owner.update(
                    "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                    company,
                    id,
                )
            return id
        }

        fun publication() =
            CollaborationDeviceKeyPublication(
                company,
                account,
                device,
                "a".repeat(64),
                0,
                now,
                null,
            )

        fun read(actor: Actor = this.actor) =
            run(actor) { repository.find(company, account, device) }

        fun <T> run(actor: Actor = this.actor, work: () -> Result<T>): Result<T> =
            transactions.run(actor, work)
    }
}
