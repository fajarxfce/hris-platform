package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceKeyMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun onlyRootOwnerCanReserveExactActiveDeviceAndOriginalParent() {
        val f = fixture()
        for (actor in
            listOf(
                f.anonymous,
                f.actor.copy(companyId = f.company),
                f.actor.copy(accountId = UUID.randomUUID()),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run(actor) { reserve(f) })
            assertTrue(success(f.run(actor) { rows(f) }).isEmpty())
        }
        for ((column, value) in
            listOf(
                "native_session_id" to "'${UUID.randomUUID()}'",
                "matrix_user_id" to "'@other:matrix.example.test'",
                "matrix_device_id" to "'HRISG_other'",
                "account_id" to "'${UUID.randomUUID()}'",
                "fingerprint" to "'${"A".repeat(64)}'",
                "reserved_at" to "'infinity'",
                "reserved_at" to "clock_timestamp()+interval '1 second'",
                "version" to "1",
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run { reserve(f, column, value) }, column)
            assertTrue(success(f.run { rows(f) }).isEmpty())
        }
        assertEquals(1, success(f.run { reserve(f) }))
        assertEquals(0L, success(f.run { rows(f) }).single()["version"])
        val worker =
            PostgresTransactionRunner(
                org.springframework.jdbc.support.JdbcTransactionManager(f.workerSource),
                org.springframework.jdbc.core.JdbcTemplate(f.workerSource),
            )
        assertTrue(
            success(
                    worker.run(f.anonymous) {
                        safeDatabaseCall {
                            org.springframework.jdbc.core
                                .JdbcTemplate(f.workerSource)
                                .queryForList("select * from collaboration_space_key_publications")
                        }
                    }
                )
                .isEmpty()
        )
    }

    @Test
    fun publicationHasOnlyOneTransitionAndCannotResetDeleteOrReplaceIdentity() {
        val f = fixture()
        success(f.run { reserve(f) })
        val original = success(f.run { rows(f) })
        assertInstanceOf(Result.Failed::class.java, f.run { reserve(f) })
        for (change in
            listOf(
                "fingerprint='${"b".repeat(64)}'",
                "native_session_id=gen_random_uuid()",
                "reserved_at=reserved_at+interval '1 microsecond'",
                "version=1",
                "published_at=clock_timestamp()",
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                f.run {
                    f.runtime.update("update collaboration_space_key_publications set $change")
                },
            )
            assertEquals(original, success(f.run { rows(f) }))
        }
        assertEquals(1, success(f.run { publish(f) }))
        val published = success(f.run { rows(f) })
        assertEquals(1L, published.single()["version"])
        assertEquals(0, success(f.run { publish(f) }))
        for (change in
            listOf(
                "version=0,published_at=null",
                "fingerprint='${"b".repeat(64)}'",
                "published_at=clock_timestamp()",
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                f.run {
                    f.runtime.update("update collaboration_space_key_publications set $change")
                },
            )
        }
        assertEquals(
            0,
            success(f.run { f.runtime.update("delete from collaboration_space_key_publications") }),
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            f.owner.update("delete from collaboration_space_key_publications")
        }
        assertEquals(published, success(f.run { rows(f) }))
    }

    @Test
    fun offboardingAndNativePurgeKeepUncertainPublicIdentityForOwnedRecoveryAndArchiveRestore() {
        val f = fixture()
        success(f.run { reserve(f) })
        val original = success(f.run { rows(f) })
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
            assertInstanceOf(Result.Failed::class.java, f.run { publish(f) }, change)
            assertEquals(original, success(f.run { rows(f) }))
            f.owner.update(restore)
        }
        f.owner.update("delete from native_sessions where id=?", f.parent)
        assertInstanceOf(Result.Failed::class.java, f.run { publish(f) })
        assertEquals(original, success(f.run { rows(f) }))
        assertTrue(
            f.owner.queryForList("select * from collaboration_space_device_sessions").isEmpty()
        )
        val restored = f.restore()
        assertEquals(
            original,
            restored.queryForList("select * from collaboration_space_key_publications"),
        )
        assertEquals(
            f.owner.queryForList("select * from collaboration_space_devices"),
            restored.queryForList("select * from collaboration_space_devices"),
        )
        assertTrue(
            restored.queryForObject(
                "select relrowsecurity and relforcerowsecurity from pg_class where oid='collaboration_space_key_publications'::regclass",
                Boolean::class.java,
            )!!
        )
    }

    @Test
    fun competingReservationsAndAcknowledgementsHaveOneWinnerWithoutReplacingFingerprint() {
        val f = fixture()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val writes =
                listOf("a", "b").map { key ->
                    pool.submit<Result<Int>> {
                        assertTrue(start.await(10, TimeUnit.SECONDS))
                        f.run { reserve(f, "fingerprint", "'${key.repeat(64)}'") }
                    }
                }
            start.countDown()
            val results = writes.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
            val before = success(f.run { rows(f) }).single()
            val publishStart = CountDownLatch(1)
            val acknowledgements =
                (0..1).map {
                    pool.submit<Result<Int>> {
                        assertTrue(publishStart.await(10, TimeUnit.SECONDS))
                        f.run { publish(f) }
                    }
                }
            publishStart.countDown()
            assertEquals(
                listOf(0, 1),
                acknowledgements.map { success(it.get(20, TimeUnit.SECONDS)) }.sorted(),
            )
            assertEquals(before["fingerprint"], success(f.run { rows(f) }).single()["fingerprint"])
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedResultCancellationAndInterruptionRollBackReservationAndPublication() {
        val f = fixture()
        for (mode in listOf("failure", "cancel", "interrupt")) {
            val action = {
                f.transactions.run<Unit>(f.actor) {
                    reserve(f)
                    when (mode) {
                        "cancel" -> throw CancellationException("owned fixture")
                        "interrupt" -> throw InterruptedException("owned fixture")
                        else -> Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))
                    }
                }
            }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { action() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { action() }
                else -> assertInstanceOf(Result.Failed::class.java, action())
            }
            assertTrue(success(f.run { rows(f) }).isEmpty())
        }
        success(f.run { reserve(f) })
        assertInstanceOf(
            Result.Failed::class.java,
            f.transactions.run<Unit>(f.actor) {
                publish(f)
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_late_denial"))
            },
        )
        assertEquals(0L, success(f.run { rows(f) }).single()["version"])
    }

    @Test
    fun publicationRechecksRealNativeExpiryAfterWaitingForDeviceGuard() {
        val f = fixture()
        success(f.run { reserve(f) })
        f.owner.update(
            "update native_sessions set access_expires_at=clock_timestamp()+interval '2 seconds' where id=?",
            f.parent,
        )
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val guard =
                pool.submit<Result<Int>> {
                    f.run {
                        f.runtime.queryForList(
                            "select id from collaboration_space_devices where id=? for update",
                            f.device,
                        )
                        held.countDown()
                        assertTrue(release.await(10, TimeUnit.SECONDS))
                        1
                    }
                }
            assertTrue(held.await(10, TimeUnit.SECONDS))
            val waiting = pool.submit<Result<Int>> { f.run { publish(f) } }
            var blocked = false
            repeat(100) {
                if (!blocked) {
                    blocked =
                        f.owner.queryForObject(
                            "select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query like 'update collaboration_space_key_publications%')",
                            Boolean::class.java,
                        ) == true
                    if (!blocked) TimeUnit.MILLISECONDS.sleep(10)
                }
            }
            assertTrue(blocked)
            TimeUnit.MILLISECONDS.sleep(2200)
            release.countDown()
            success(guard.get(10, TimeUnit.SECONDS))
            assertInstanceOf(Result.Failed::class.java, waiting.get(10, TimeUnit.SECONDS))
            assertEquals(0L, success(f.run { rows(f) }).single()["version"])
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun fixture(): SpaceGatewayMigrationFixture =
        SpaceGatewayMigrationFixture(postgres).also { f ->
            f.migrate()
            Flyway.configure().dataSource(f.source).target("128").load().migrate()
            f.owner.execute(
                "grant select,insert,update,delete on collaboration_space_key_publications to space_gateway_fixture,space_gateway_worker"
            )
        }

    private fun reserve(
        f: SpaceGatewayMigrationFixture,
        column: String? = null,
        value: String? = null,
    ): Int {
        val values =
            linkedMapOf(
                "space_id" to "'${f.space}'",
                "account_id" to "'${f.account}'",
                "device_id" to "'${f.device}'",
                "native_session_id" to "'${f.parent}'",
                "matrix_user_id" to "'${f.user}'",
                "matrix_device_id" to "'HRISG_${f.device.toString().replace("-", "")}'",
                "fingerprint" to "'${"a".repeat(64)}'",
                "version" to "0",
                "reserved_at" to "clock_timestamp()",
            )
        if (column != null) values[column] = requireNotNull(value)
        return f.runtime.update(
            "insert into collaboration_space_key_publications(${values.keys.joinToString(",")}) values(${values.values.joinToString(",")})"
        )
    }

    private fun publish(f: SpaceGatewayMigrationFixture) =
        f.runtime.update(
            "update collaboration_space_key_publications set version=1,published_at=clock_timestamp() where space_id=? and device_id=? and version=0",
            f.space,
            f.device,
        )

    private fun rows(f: SpaceGatewayMigrationFixture): List<Map<String, Any?>> =
        f.runtime.queryForList("select * from collaboration_space_key_publications")

    private fun <T> success(result: Result<T>): T = (result as Result.Success).value
}
