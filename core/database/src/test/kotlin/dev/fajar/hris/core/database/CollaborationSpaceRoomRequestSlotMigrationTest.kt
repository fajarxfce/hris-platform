package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceRoomRequestSlotMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun upgradeKeepsExistingSlotsAndAdmitsRoomControlWithoutGatewayCoordinates() {
        val f = fixture("133")
        success(f.run { claim(f) })
        success(f.run { claim(f, kind = "PROVIDER_RENEWAL", gateway = null, version = null) })
        val before = success(f.run { rows(f) })
        fun lifetime() =
            f.owner.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conrelid='collaboration_space_request_leases'::regclass and conname='collaboration_space_request_leases_check'",
                String::class.java,
            )
        val originalLifetime = requireNotNull(lifetime())
        assertTrue(
            originalLifetime.contains("expires_at") && originalLifetime.contains("started_at")
        )
        Flyway.configure().dataSource(f.source).target("134").load().migrate()
        assertEquals(before, success(f.run { rows(f) }))
        assertEquals(originalLifetime, lifetime())
        for (actor in
            listOf(
                f.anonymous,
                f.actor.copy(companyId = f.company),
                f.actor.copy(accountId = UUID.randomUUID()),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run(actor) { room(f) })
            assertTrue(success(f.run(actor) { rows(f) }).isEmpty())
        }
        for (invalid in listOf("ROOM_PREPARATION", "UNKNOWN")) assertInstanceOf(
            Result.Failed::class.java,
            f.run { claim(f, kind = invalid) },
        )
        val control = success(f.run { room(f) }).single()
        assertEquals(2, control["slot"])
        assertEquals("ROOM_PREPARATION", control["kind"])
        assertNull(control["gateway_id"])
        assertNull(control["gateway_version"])
        assertInstanceOf(
            Result.Failed::class.java,
            f.run {
                f.runtime.update(
                    "update collaboration_space_request_leases set kind='GATEWAY',gateway_id=?,gateway_version=0,token=gen_random_uuid() where slot=2",
                    f.gateway,
                )
            },
        )
        assertEquals(control, success(f.run { rows(f) }).single { it["slot"] == 2 })
    }

    @Test
    fun competingRoomAndGatewayCallsShareSevenSlotsAndLeaveRenewalReserved() {
        val f = fixture()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures =
                (0..7).map { index ->
                    pool.submit<Result<List<Map<String, Any?>>>> {
                        assertTrue(start.await(10, TimeUnit.SECONDS))
                        f.run { if (index % 2 == 0) room(f) else claim(f) }
                    }
                }
            start.countDown()
            val leases = futures.flatMap { success(it.get(20, TimeUnit.SECONDS)) }
            assertEquals(7, leases.size)
            assertEquals((1..7).toSet(), leases.map { it["slot"] }.toSet())
            assertEquals(setOf("GATEWAY", "ROOM_PREPARATION"), leases.map { it["kind"] }.toSet())
            assertEquals(7, leases.map { it["token"] }.toSet().size)
            assertTrue(success(f.run { room(f) }).isEmpty())
            assertTrue(success(f.run { claim(f) }).isEmpty())
            assertEquals(
                0,
                success(
                        f.run {
                            claim(f, kind = "PROVIDER_RENEWAL", gateway = null, version = null)
                        }
                    )
                    .single()["slot"],
            )
            assertEquals(8, success(f.run { rows(f) }).size)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun onlyExpiredSlotsCanChangePurposeAndOldTokensCannotReleaseTheirSuccessor() {
        val f = fixture()
        val gateway = success(f.run { claim(f, until = Instant.now().plusSeconds(2)) }).single()
        awaitExpiry(gateway)
        val control = success(f.run { room(f, Instant.now().plusSeconds(2)) }).single()
        assertEquals(gateway["slot"], control["slot"])
        assertNotEquals(gateway["token"], control["token"])
        assertEquals("ROOM_PREPARATION", control["kind"])
        assertNull(control["gateway_id"])
        assertEquals(0, success(f.run { release(f, gateway) }))
        awaitExpiry(control)
        val next = success(f.run { claim(f) }).single()
        assertEquals(control["slot"], next["slot"])
        assertEquals("GATEWAY", next["kind"])
        assertEquals(f.gateway, next["gateway_id"])
        assertNotEquals(control["token"], next["token"])
        assertEquals(0, success(f.run { release(f, control) }))
        assertEquals(listOf(next), success(f.run { rows(f) }))
    }

    @Test
    fun roomClaimRollbackCancellationAndRevocationRetainOnlyCleanupAuthority() {
        val f = fixture()
        assertInstanceOf(
            Result.Failed::class.java,
            f.transactions.run<Unit>(f.actor) {
                assertEquals(1, room(f).size)
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))
            },
        )
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            f.transactions.run<Unit>(f.actor) {
                assertEquals(1, room(f).size)
                throw java.util.concurrent.CancellationException("fixture cancellation")
            }
        }
        assertTrue(success(f.run { rows(f) }).isEmpty())
        val control = success(f.run { room(f, Instant.now().plusSeconds(2)) }).single()
        val devices = f.owner.queryForList("select * from collaboration_space_devices")
        val provider = f.owner.queryForList("select * from collaboration_space_provider_sessions")
        f.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        f.owner.update("delete from native_sessions where id=?", f.parent)
        assertInstanceOf(Result.Failed::class.java, f.run { room(f) })
        assertEquals(listOf(control), success(f.run { rows(f) }))
        val worker = JdbcTemplate(f.workerSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(f.workerSource), worker)
        assertEquals(
            0,
            success(transactions.run(f.anonymous) { safeDatabaseCall { cleanup(worker) } }),
        )
        awaitExpiry(control)
        assertEquals(
            1,
            success(transactions.run(f.anonymous) { safeDatabaseCall { cleanup(worker) } }),
        )
        assertEquals(0, success(f.run { release(f, control) }))
        assertEquals(devices, f.owner.queryForList("select * from collaboration_space_devices"))
        assertEquals(
            provider,
            f.owner.queryForList("select * from collaboration_space_provider_sessions"),
        )
    }

    @Test
    fun archiveRestoresAllThreeKindsAndTheOriginalIdentityWithoutNewSecrets() {
        val f = fixture()
        success(f.run { room(f) })
        success(f.run { claim(f) })
        success(f.run { claim(f, kind = "PROVIDER_RENEWAL", gateway = null, version = null) })
        val expected = success(f.run { rows(f) })
        val provider = f.owner.queryForList("select * from collaboration_space_provider_sessions")
        val restored = f.restore()
        assertEquals(
            expected,
            restored.queryForList("select * from collaboration_space_request_leases order by slot"),
        )
        assertEquals(
            provider,
            restored.queryForList("select * from collaboration_space_provider_sessions"),
        )
        assertEquals(
            f.user,
            restored.queryForObject(
                "select matrix_user_id from collaboration_space_devices",
                String::class.java,
            ),
        )
    }

    @Test
    fun deviceGuardWaitCannotAdmitARequestAfterItsOriginalDeadline() {
        val f = fixture()
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val holder =
                pool.submit<Result<Unit>> {
                    f.run {
                        f.runtime.queryForList(
                            "select id from collaboration_space_devices where id=? for update",
                            f.device,
                        )
                        locked.countDown()
                        assertTrue(release.await(10, TimeUnit.SECONDS))
                    }
                }
            assertTrue(locked.await(10, TimeUnit.SECONDS))
            val until = Instant.now().plusSeconds(3)
            val waiting =
                pool.submit<Result<Int>> {
                    f.run {
                        f.runtime.update(
                            "insert into collaboration_space_request_leases(space_id,account_id,device_id,native_session_id,slot,kind,token,started_at,expires_at) values(?,?,?,?,1,'ROOM_PREPARATION',gen_random_uuid(),clock_timestamp(),?::timestamptz)",
                            f.space,
                            f.account,
                            f.device,
                            f.parent,
                            until.toString(),
                        )
                    }
                }
            val observeUntil = Instant.now().plusSeconds(2)
            var observed = false
            while (Instant.now() < observeUntil) {
                observed =
                    f.owner.queryForObject(
                        "select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query like 'insert into collaboration_space_request_leases%')",
                        Boolean::class.java,
                    ) == true
                if (observed) break
                Thread.sleep(10)
            }
            assertTrue(observed, "Actual request did not wait on the held device row")
            val remaining = java.time.Duration.between(Instant.now(), until).toNanos()
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining + 20_000_000)
            release.countDown()
            success(holder.get(10, TimeUnit.SECONDS))
            assertInstanceOf(Result.Failed::class.java, waiting.get(10, TimeUnit.SECONDS))
            assertTrue(success(f.run { rows(f) }).isEmpty())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun room(
        f: SpaceGatewayMigrationFixture,
        until: Instant = Instant.now().plusSeconds(30),
    ) = claim(f, kind = "ROOM_PREPARATION", gateway = null, version = null, until = until)

    private fun awaitExpiry(row: Map<String, Any?>) {
        val until = (row["expires_at"] as java.sql.Timestamp).toInstant()
        val remaining = java.time.Duration.between(Instant.now(), until).toNanos()
        if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining + 20_000_000)
    }

    private fun fixture(target: String = "134"): SpaceGatewayMigrationFixture {
        val f = SpaceGatewayMigrationFixture(postgres).also { it.migrate() }
        success(f.run { f.insert() })
        Flyway.configure().dataSource(f.source).target(target).load().migrate()
        f.owner.execute(
            "grant select,insert,update,delete on collaboration_space_request_leases to space_gateway_fixture,space_gateway_worker"
        )
        return f
    }

    private fun claim(
        f: SpaceGatewayMigrationFixture,
        kind: String = "GATEWAY",
        gateway: UUID? = f.gateway,
        version: Long? = 0,
        parent: UUID = f.parent,
        until: Instant = Instant.now().plusSeconds(30),
    ): List<Map<String, Any?>> =
        f.runtime.queryForList(
            "select * from claim_collaboration_space_request(?::uuid,?::uuid,?::uuid,?::uuid,?::varchar,?::uuid,?::bigint,?::timestamptz)",
            f.space,
            f.account,
            f.device,
            parent,
            kind,
            gateway,
            version,
            until.toString(),
        )

    private fun rows(f: SpaceGatewayMigrationFixture) =
        f.runtime.queryForList("select * from collaboration_space_request_leases order by slot")

    private fun release(f: SpaceGatewayMigrationFixture, lease: Map<String, Any?>) =
        f.runtime.update(
            "delete from collaboration_space_request_leases where space_id=? and account_id=? and device_id=? and native_session_id=? and slot=? and token=?",
            f.space,
            f.account,
            f.device,
            f.parent,
            lease["slot"],
            lease["token"],
        )

    private fun cleanup(db: JdbcTemplate) =
        db.update(
            "with expired as (select space_id,device_id,slot from collaboration_space_request_leases where expires_at<=clock_timestamp() order by expires_at,space_id,device_id,slot limit 100 for update skip locked) delete from collaboration_space_request_leases l using expired e where l.space_id=e.space_id and l.device_id=e.device_id and l.slot=e.slot"
        )

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            (result as? Result.Failed)?.failure?.code ?: "Unexpected result"
        }
        return (result as Result.Success).value
    }
}
