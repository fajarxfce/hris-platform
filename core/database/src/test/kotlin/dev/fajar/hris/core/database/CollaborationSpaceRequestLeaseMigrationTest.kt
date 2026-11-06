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
class CollaborationSpaceRequestLeaseMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun groupSlotsEnforceScopeCurrentParentGatewayAndExactBoundedPurpose() {
        val f = fixture()
        for (actor in
            listOf(
                f.anonymous,
                f.actor.copy(companyId = f.company),
                f.actor.copy(accountId = UUID.randomUUID()),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run(actor) { claim(f) })
            assertTrue(success(f.run(actor) { rows(f) }).isEmpty())
        }
        for (bad in listOf(-1L, 1L, 1000000L)) assertInstanceOf(
            Result.Failed::class.java,
            f.run { claim(f, version = bad) },
        )
        for (until in
            listOf(Instant.now().minusSeconds(1), Instant.now().plusSeconds(61))) assertInstanceOf(
            Result.Failed::class.java,
            f.run { claim(f, until = until) },
        )
        assertInstanceOf(Result.Failed::class.java, f.run { claim(f, gateway = UUID.randomUUID()) })
        assertTrue(success(f.run { claim(f, parent = UUID.randomUUID()) }).isEmpty())
        assertTrue(success(f.run { rows(f) }).isEmpty())
        val gateway = success(f.run { claim(f) }).single()
        assertEquals(1, gateway["slot"])
        assertEquals(f.gateway, gateway["gateway_id"])
        val renewal =
            success(f.run { claim(f, kind = "PROVIDER_RENEWAL", gateway = null, version = null) })
                .single()
        assertEquals(0, renewal["slot"])
        assertNull(renewal["gateway_id"])
        assertInstanceOf(Result.Failed::class.java, f.run { claim(f, kind = "PROVIDER_RENEWAL") })
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { claim(f, gateway = null, version = null) },
        )
        assertEquals(2, success(f.run { rows(f) }).size)
        for (change in
            listOf(
                "expires_at=expires_at+interval '1 second'",
                "token=gen_random_uuid()",
                "slot=7",
                "native_session_id=gen_random_uuid()",
            )) assertInstanceOf(
            Result.Failed::class.java,
            f.run {
                f.runtime.update(
                    "update collaboration_space_request_leases set $change where slot=1"
                )
            },
        )
    }

    @Test
    fun concurrentRequestsRespectSevenGatewaySlotsAndReserveProviderRenewalCapacity() {
        val f = fixture()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures =
                (0..7).map {
                    pool.submit<Result<List<Map<String, Any?>>>> {
                        assertTrue(start.await(10, TimeUnit.SECONDS))
                        f.run { claim(f) }
                    }
                }
            start.countDown()
            val claimed = futures.flatMap { success(it.get(20, TimeUnit.SECONDS)) }
            assertEquals((1..7).toSet(), claimed.map { it["slot"] }.toSet())
            assertEquals(7, claimed.map { it["token"] }.toSet().size)
            assertTrue(success(f.run { claim(f) }).isEmpty())
            val renewal =
                success(
                        f.run {
                            claim(f, kind = "PROVIDER_RENEWAL", gateway = null, version = null)
                        }
                    )
                    .single()
            assertEquals(0, renewal["slot"])
            assertTrue(
                success(
                        f.run {
                            claim(f, kind = "PROVIDER_RENEWAL", gateway = null, version = null)
                        }
                    )
                    .isEmpty()
            )
            val old = claimed.first()
            assertEquals(1, success(f.run { release(f, old) }))
            val replacement = success(f.run { claim(f) }).single()
            assertEquals(old["slot"], replacement["slot"])
            assertNotEquals(old["token"], replacement["token"])
            assertEquals(0, success(f.run { release(f, old) }))
            assertEquals(8, success(f.run { rows(f) }).size)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedTransactionsAndCancellationDoNotLeaveClaimedRequests() {
        val f = fixture()
        assertInstanceOf(
            Result.Failed::class.java,
            f.transactions.run(f.actor) {
                assertEquals(1, claim(f).size)
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))
            },
        )
        assertTrue(success(f.run { rows(f) }).isEmpty())
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            f.transactions.run<Unit>(f.actor) {
                assertEquals(1, claim(f).size)
                throw java.util.concurrent.CancellationException("fixture cancellation")
            }
        }
        assertTrue(success(f.run { rows(f) }).isEmpty())
        assertThrows(InterruptedException::class.java) {
            f.transactions.run<Unit>(f.actor) {
                assertEquals(1, claim(f).size)
                throw InterruptedException("fixture interruption")
            }
        }
        assertTrue(success(f.run { rows(f) }).isEmpty())
    }

    @Test
    fun currentSecurityAndEligibilityAreRequiredForNewRequestsButDoNotHideCleanupFacts() {
        val f = fixture()
        val first = success(f.run { claim(f) }).single()
        for ((change, restore) in
            listOf(
                "update accounts set active=false,version=version+1 where id='${f.account}'" to
                    "update accounts set active=true,version=version+1 where id='${f.account}'",
                "update native_sessions set credential_version=-1 where id='${f.parent}'" to
                    "update native_sessions set credential_version=0 where id='${f.parent}'",
                "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id='${f.parent}'" to
                    "update native_sessions set access_expires_at='${f.at.plusSeconds(900)}' where id='${f.parent}'",
                "update company_memberships set active=false,version=version+1 where account_id='${f.account}'" to
                    "update company_memberships set active=true,version=version+1 where account_id='${f.account}'",
            )) {
            f.owner.update(change)
            assertInstanceOf(Result.Failed::class.java, f.run { claim(f) })
            assertEquals(listOf(first), success(f.run { rows(f) }))
            f.owner.update(restore)
        }
        val oldDevice = f.owner.queryForList("select * from collaboration_space_devices")
        val oldProvider =
            f.owner.queryForList("select * from collaboration_space_provider_sessions")
        val oldGateway = f.owner.queryForList("select * from collaboration_space_gateway_sessions")
        f.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        f.owner.update("delete from native_sessions where id=?", f.parent)
        assertEquals(listOf(first), success(f.run { rows(f) }))
        assertInstanceOf(Result.Failed::class.java, f.run { claim(f) })
        assertEquals(1, success(f.run { release(f, first) }))
        assertEquals(0, success(f.run { release(f, first) }))
        assertEquals(oldDevice, f.owner.queryForList("select * from collaboration_space_devices"))
        assertEquals(
            oldProvider,
            f.owner.queryForList("select * from collaboration_space_provider_sessions"),
        )
        assertEquals(
            oldGateway,
            f.owner.queryForList("select * from collaboration_space_gateway_sessions"),
        )
    }

    @Test
    fun elapsedReclamationAndWorkerCleanupSkipLockedRowsWithoutGrantingLiveAuthority() {
        val f = fixture()
        val worker = JdbcTemplate(f.workerSource)
        val workerTransactions =
            PostgresTransactionRunner(JdbcTransactionManager(f.workerSource), worker)
        val old = success(f.run { claim(f, until = Instant.now().plusSeconds(2)) }).single()
        assertTrue(
            success(
                    workerTransactions.run(f.anonymous) {
                        safeDatabaseCall {
                            worker.queryForList("select * from collaboration_space_request_leases")
                        }
                    }
                )
                .isEmpty()
        )
        val expires = (old["expires_at"] as java.sql.Timestamp).toInstant()
        val remaining = java.time.Duration.between(Instant.now(), expires).toNanos()
        if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining + 20_000_000)
        val fresh = success(f.run { claim(f) }).single()
        assertEquals(old["slot"], fresh["slot"])
        assertNotEquals(old["token"], fresh["token"])
        assertEquals(0, success(f.run { release(f, old) }))
        val expiring =
            success(
                    f.run {
                        claim(
                            f,
                            kind = "PROVIDER_RENEWAL",
                            gateway = null,
                            version = null,
                            until = Instant.now().plusSeconds(2),
                        )
                    }
                )
                .single()
        val until = (expiring["expires_at"] as java.sql.Timestamp).toInstant()
        val wait = java.time.Duration.between(Instant.now(), until).toNanos()
        if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait + 20_000_000)
        val locked = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val holder =
                pool.submit<Result<Unit>> {
                    f.run {
                        f.runtime.queryForList(
                            "select * from collaboration_space_request_leases where slot=0 for update"
                        )
                        locked.countDown()
                        assertTrue(finish.await(10, TimeUnit.SECONDS))
                    }
                }
            assertTrue(locked.await(10, TimeUnit.SECONDS))
            assertEquals(
                0,
                success(
                    workerTransactions.run(f.anonymous) { safeDatabaseCall { cleanup(worker) } }
                ),
            )
            finish.countDown()
            success(holder.get(10, TimeUnit.SECONDS))
        } finally {
            finish.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
        assertEquals(
            1,
            success(workerTransactions.run(f.anonymous) { safeDatabaseCall { cleanup(worker) } }),
        )
        assertEquals(listOf(fresh), success(f.run { rows(f) }))
        assertEquals(
            0,
            success(
                workerTransactions.run(f.anonymous) {
                    safeDatabaseCall {
                        worker.update("delete from collaboration_space_request_leases")
                    }
                }
            ),
        )
        assertEquals(1, success(f.run { release(f, fresh) }))
    }

    @Test
    fun archiveRestoreRetainsOnlyOperationalCoordinatesWithoutChangingOriginalIdentity() {
        val f = fixture()
        success(f.run { claim(f) })
        success(f.run { claim(f, kind = "PROVIDER_RENEWAL", gateway = null, version = null) })
        val expected = success(f.run { rows(f) })
        val restored = f.restore()
        assertEquals(
            expected,
            restored.queryForList("select * from collaboration_space_request_leases order by slot"),
        )
        assertEquals(
            f.user,
            restored.queryForObject(
                "select matrix_user_id from collaboration_space_devices",
                String::class.java,
            ),
        )
        assertEquals(
            1,
            restored.queryForObject(
                "select count(*) from collaboration_space_gateway_sessions",
                Int::class.java,
            ),
        )
    }

    private fun fixture(): SpaceGatewayMigrationFixture {
        val f = SpaceGatewayMigrationFixture(postgres).also { it.migrate() }
        success(f.run { f.insert() })
        Flyway.configure().dataSource(f.source).target("127").load().migrate()
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
