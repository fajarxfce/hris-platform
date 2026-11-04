package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceGatewayMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun migrationAndOpaqueLookupPreserveOwnedScopeWithoutGrantingCompanyAccess() {
        val f = SpaceGatewayMigrationFixture(postgres)
        f.migrate()
        assertTrue(success(f.rows()).isEmpty())
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = f.company),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run(actor) { f.insert() })
            assertTrue(success(f.rows(actor)).isEmpty())
        }
        assertInstanceOf(Result.Failed::class.java, f.run { f.insert(session = UUID.randomUUID()) })
        assertInstanceOf(Result.Failed::class.java, f.run { f.insert(build = 1_000_000_000) })
        assertEquals(1, success(f.run { f.insert() }))
        val owned = success(f.rows())
        for (hash in listOf(f.access, f.refresh)) assertEquals(owned, success(f.lookup(hash)))
        assertTrue(success(f.lookup("e".repeat(64))).isEmpty())
        assertTrue(success(f.lookup("invalid")).isEmpty())
        assertTrue(
            success(f.lookup(f.access, setting = "app.collaboration_gateway_lookup")).isEmpty()
        )
        assertTrue(
            success(f.lookup(f.access, current = f.actor.copy(accountId = UUID.randomUUID())))
                .isEmpty()
        )
        assertTrue(
            success(f.lookup(f.access, current = f.anonymous.copy(companyId = f.company))).isEmpty()
        )
        assertTrue(success(f.rows(f.anonymous)).isEmpty())
        assertEquals(
            0,
            f.runtime.queryForObject(
                "select count(*) from collaboration_space_gateway_sessions",
                Int::class.java,
            ),
        )
        f.owner.update(
            "insert into companies(id,code,name,timezone) values(?,'FOREIGN','Other fictional company','UTC')",
            UUID.randomUUID(),
        )
        // Existing membership permits its own company-directory label, never another company.
        assertEquals(
            1,
            success(
                f.run {
                    f.runtime.queryForObject("select count(*) from companies", Int::class.java)
                }
            ),
        )
        assertEquals(
            0,
            success(
                f.run(f.anonymous) {
                    f.runtime.queryForObject(
                        "select set_config('app.collaboration_space_gateway_lookup',?,true)",
                        String::class.java,
                        f.access,
                    )
                    f.runtime.queryForObject("select count(*) from companies", Int::class.java)
                }
            ),
        )
        assertEquals(
            0,
            success(f.run { f.runtime.update("delete from collaboration_space_gateway_sessions") }),
        )
        assertThrows(DataAccessException::class.java) {
            f.owner.update("delete from collaboration_space_gateway_sessions")
        }
    }

    @Test
    fun rotationPinsNativeOwnershipHardExpiryAndTheExactPreviousRefresh() {
        val f = SpaceGatewayMigrationFixture(postgres)
        f.migrate()
        success(f.run { f.insert() })
        val original = success(f.rows())
        for (assignment in
            listOf(
                "version=2",
                "version=1,native_session_id=gen_random_uuid()",
                "version=1,space_id=gen_random_uuid()",
                "version=1,account_id=gen_random_uuid()",
                "version=1,device_id=gen_random_uuid()",
                "version=1,expires_at=expires_at+interval '1 second'",
                "version=1,created_at=created_at-interval '1 second'",
                "version=1,access_expires_at=rotated_at+interval '16 minutes'",
                "version=1,rotated_at=clock_timestamp()+interval '1 second'",
                "version=1,replay_until=rotated_at+interval '61 seconds'",
                "version=1,replay_encrypted=null,replay_kind=null,bootstrap_operation_id=null",
            )) assertInstanceOf(Result.Failed::class.java, f.run { f.change(assignment) })
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.rotate(",previous_refresh_hash='${"e".repeat(64)}'") },
        )
        assertInstanceOf(Result.Failed::class.java, f.run { f.rotate(",client_build=6") })
        assertEquals(original, success(f.rows()))
        assertEquals(1, success(f.run { f.rotate() }))
        val rotated = success(f.rows()).single()
        assertEquals(1L, rotated["version"])
        assertEquals(f.refresh, rotated["previous_refresh_hash"])
        assertEquals(rotated, success(f.lookup(f.refresh)).single())
        assertTrue(success(f.lookup(f.access)).isEmpty())
        assertEquals(0, success(f.run { f.rotate() }))
        assertEquals(original.single()["expires_at"], rotated["expires_at"])
        assertEquals(original.single()["created_at"], rotated["created_at"])
    }

    @Test
    fun currentNativeSecurityAndEligibilityAreRequiredForEveryLiveWrite() {
        val f = SpaceGatewayMigrationFixture(postgres)
        f.migrate()
        success(f.run { f.insert() })
        val original = success(f.rows())
        for ((change, restore) in
            listOf(
                "update accounts set active=false,version=version+1 where id='${f.account}'" to
                    "update accounts set active=true,version=version+1 where id='${f.account}'",
                "update native_sessions set credential_version=-1 where id='${f.parent}'" to
                    "update native_sessions set credential_version=0 where id='${f.parent}'",
                "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id='${f.parent}'" to
                    "update native_sessions set access_expires_at='${f.at.plusSeconds(900)}' where id='${f.parent}'",
                "update native_sessions set revoked_at=clock_timestamp() where id='${f.parent}'" to
                    "update native_sessions set revoked_at=null where id='${f.parent}'",
                "update company_memberships set active=false,version=version+1 where account_id='${f.account}'" to
                    "update company_memberships set active=true,version=version+1 where account_id='${f.account}'",
            )) {
            f.owner.update(change)
            assertInstanceOf(Result.Failed::class.java, f.run { f.rotate() })
            assertEquals(original, success(f.rows()))
            f.owner.update(restore)
        }
        assertEquals(1, success(f.run { f.rotate() }))
    }

    @Test
    fun purgeAndOffboardingRetainGenerationWhileOnlyIrreversibleRetirementIsAllowed() {
        val f = SpaceGatewayMigrationFixture(postgres)
        f.migrate()
        success(f.run { f.insert() })
        val original = success(f.rows()).single()
        f.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        f.owner.update("delete from native_sessions where id=?", f.parent)
        assertEquals(original, success(f.rows()).single())
        assertInstanceOf(Result.Failed::class.java, f.run { f.rotate() })
        val at = Instant.now()
        assertInstanceOf(Result.Failed::class.java, f.run { f.retire(at) })
        success(f.run { f.revoke(at) })
        assertInstanceOf(Result.Failed::class.java, f.run { f.retire(at.minusSeconds(1)) })
        assertEquals(1, success(f.run { f.retire(at) }))
        val retired = success(f.rows()).single()
        assertEquals(1L, retired["version"])
        for (field in
            listOf(
                "access_hash",
                "refresh_hash",
                "replay_encrypted",
                "previous_refresh_hash",
            )) assertNull(retired[field])
        for (field in
            listOf(
                "space_id",
                "account_id",
                "device_id",
                "native_session_id",
                "created_at",
                "rotated_at",
                "expires_at",
                "access_expires_at",
            )) assertEquals(original[field], retired[field])
        assertInstanceOf(Result.Failed::class.java, f.run { f.change("version=2", version = 1) })
        assertTrue(success(f.lookup(f.refresh)).isEmpty())
        val restored = f.restore()
        assertEquals(
            listOf(retired),
            restored.queryForList("select * from collaboration_space_gateway_sessions"),
        )
        assertEquals(
            0,
            restored.queryForObject(
                "select count(*) from collaboration_space_device_sessions",
                Int::class.java,
            ),
        )
        assertThrows(DataAccessException::class.java) {
            restored.update("update collaboration_space_gateway_sessions set version=2")
        }
        assertThrows(DataAccessException::class.java) {
            restored.update("delete from collaboration_space_gateway_sessions")
        }
    }

    @Test
    fun expiredReplayMaintenanceCannotRotateCredentialsOrRestoreAccessAfterPurge() {
        val f = SpaceGatewayMigrationFixture(postgres, ageSeconds = 180)
        f.migrate()
        success(f.run { f.insert() })
        f.owner.update("delete from native_sessions where id=?", f.parent)
        f.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        val before = success(f.rows()).single()
        val worker = JdbcTemplate(f.workerSource)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(f.workerSource), worker)
        fun mutate(sql: String) = tx.run(f.anonymous) { safeDatabaseCall { worker.update(sql) } }
        assertInstanceOf(
            Result.Failed::class.java,
            mutate(
                "update collaboration_space_gateway_sessions set version=version+1,access_hash='${"e".repeat(64)}',replay_kind=null,bootstrap_operation_id=null,previous_refresh_hash=null,replay_encrypted=null"
            ),
        )
        assertEquals(
            1,
            success(
                mutate(
                    "update collaboration_space_gateway_sessions set replay_kind=null,bootstrap_operation_id=null,previous_refresh_hash=null,replay_encrypted=null"
                )
            ),
        )
        val after = success(f.rows()).single()
        for ((field, value) in before) if (
            field !in
                setOf(
                    "replay_kind",
                    "bootstrap_operation_id",
                    "previous_refresh_hash",
                    "replay_encrypted",
                )
        )
            assertEquals(value, after[field])
        assertNull(after["replay_encrypted"])
        assertEquals(before["version"], after["version"])
        assertInstanceOf(
            Result.Failed::class.java,
            mutate(
                "update collaboration_space_gateway_sessions set replay_kind='BOOTSTRAP',bootstrap_operation_id=gen_random_uuid(),replay_encrypted='resurrected'"
            ),
        )
    }

    @Test
    fun competingRotationsCommitOneVersionAndARejectedResultRollsBackTheWrite() {
        val f = SpaceGatewayMigrationFixture(postgres)
        f.migrate()
        success(f.run { f.insert() })
        val original = success(f.rows())
        val rolledBack: Result<Unit> =
            f.transactions.run(f.actor) {
                assertEquals(1, f.rotate())
                Result.Failed(
                    dev.fajar.hris.core.domain.Failure(
                        dev.fajar.hris.core.domain.FailureKind.CONFLICT,
                        "fixture_rejected_after_write",
                    )
                )
            }
        assertInstanceOf(Result.Failed::class.java, rolledBack)
        assertEquals(original, success(f.rows()))
        val pool = Executors.newFixedThreadPool(2)
        val go = CountDownLatch(1)
        try {
            val results =
                (1..2).map {
                    pool.submit<Result<Int>> {
                        check(go.await(5, TimeUnit.SECONDS))
                        f.run { f.rotate() }
                    }
                }
            go.countDown()
            assertEquals(
                listOf(0, 1),
                results.map { success(it.get(10, TimeUnit.SECONDS)) }.sorted(),
            )
            assertEquals(1L, success(f.rows()).single()["version"])
        } finally {
            go.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun exhaustedLiveVersionStillHasExactlyOneTerminalRevocation() {
        val f = SpaceGatewayMigrationFixture(postgres)
        f.migrate()
        success(f.run { f.insert() })
        // Isolated migration-owner fixture; avoid a million rotations without bypassing checks.
        f.owner.execute(
            "alter table collaboration_space_gateway_sessions disable trigger protect_collaboration_space_gateway_session"
        )
        try {
            f.owner.update("update collaboration_space_gateway_sessions set version=999999")
        } finally {
            f.owner.execute(
                "alter table collaboration_space_gateway_sessions enable trigger protect_collaboration_space_gateway_session"
            )
        }
        assertInstanceOf(Result.Failed::class.java, f.run { f.rotate(version = 999999) })
        val at = Instant.now()
        success(f.run { f.revoke(at) })
        assertEquals(1, success(f.run { f.retire(at, version = 999999) }))
        assertEquals(1000000L, success(f.rows()).single()["version"])
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.change("version=1000001", version = 1000000) },
        )
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
