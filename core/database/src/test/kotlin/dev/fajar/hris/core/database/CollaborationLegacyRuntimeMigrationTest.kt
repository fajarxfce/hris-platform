package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationLegacyRuntimeMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun metadataIsOwnedAndDoesNotAutomaticallyFreezeOrGrantAnotherCompanyCredential() {
        with(Fixture()) {
            val old = sql.queryForList("select * from collaboration_provider_sessions")
            migrate()
            assertTrue(heads().isEmpty())
            assertTrue(leases().isEmpty())
            assertEquals(1, success(ensure()))
            assertEquals("LEGACY", heads().single()["phase"])
            assertEquals(old, sql.queryForList("select * from collaboration_provider_sessions"))
            assertEquals(heads(), heads(actor.copy(companyId = employer)))
            assertTrue(heads(actor.copy(accountId = peer)).isEmpty())
            assertInstanceOf(
                Result.Failed::class.java,
                ensure(actor.copy(accountId = peer, companyId = company)),
            )
            assertInstanceOf(Result.Failed::class.java, claim(current = actor))
            assertInstanceOf(
                Result.Failed::class.java,
                claim(current = actor.copy(companyId = employer)),
            )
            assertEquals(
                0,
                success(
                    run(actor.copy(accountId = peer)) {
                        runtime.update("update collaboration_legacy_runtime set version=1")
                    }
                ),
            )
            assertEquals(
                0,
                success(run { runtime.update("delete from collaboration_legacy_runtime") }),
            )
            for (assignment in
                listOf(
                    "native_session_id='${UUID.randomUUID()}'",
                    "created_at=created_at-interval '1 second'",
                    "version=1",
                    "phase='DRAINING'",
                )) {
                assertInstanceOf(
                    Result.Failed::class.java,
                    run { runtime.update("update collaboration_legacy_runtime set $assignment") },
                )
            }
            assertTrue(
                success(
                        run {
                            runtime.queryForList("select * from collaboration_provider_sessions")
                        }
                    )
                    .isEmpty()
            )
        }
    }

    @Test
    fun eightReusableSlotsBoundConcurrencyAndLateReleaseCannotFreeAReplacement() {
        with(Fixture()) {
            migrate()
            success(ensure())
            val pool = Executors.newFixedThreadPool(4)
            val admitted =
                try {
                    (0 until 16)
                        .map { pool.submit<List<Map<String, Any?>>> { success(claim()) } }
                        .flatMap { it.get(10, TimeUnit.SECONDS) }
                } finally {
                    pool.shutdownNow()
                    assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
                }
            assertEquals(8, admitted.size)
            assertEquals((0..7).toSet(), admitted.map { it["slot"] }.toSet())
            assertEquals(8, leases().size)
            assertTrue(success(claim()).isEmpty())
            val first = admitted.first()["token"] as UUID
            assertEquals(0, success(release(UUID.randomUUID())))
            for (assignment in
                listOf(
                    "expires_at=expires_at+interval '1 second'",
                    "token='${UUID.randomUUID()}'",
                    "runtime_version=1",
                    "slot=8",
                    "released_at=clock_timestamp()+interval '1 minute'",
                )) {
                assertInstanceOf(
                    Result.Failed::class.java,
                    run {
                        runtime.update(
                            "update collaboration_legacy_runtime_leases set $assignment where token=?",
                            first,
                        )
                    },
                )
            }
            assertEquals(1, success(release(first)))
            val replacement = success(claim(now().plusSeconds(1))).single()
            assertNotEquals(first, replacement["token"])
            assertEquals(0, success(release(first)))
            assertTrue(success(claim()).isEmpty())
            Thread.sleep(1100)
            val reclaimed = success(claim()).single()
            assertEquals(replacement["slot"], reclaimed["slot"])
            assertNotEquals(replacement["token"], reclaimed["token"])
            assertEquals(0, success(release(replacement["token"] as UUID)))
            assertEquals(8, leases().size)
            assertInstanceOf(Result.Failed::class.java, claim(now().plusSeconds(61)))
            assertInstanceOf(Result.Failed::class.java, claim(now().minusSeconds(1)))
        }
    }

    @Test
    fun freezeSerializesWithAClaimAndNeverReopensLegacyAcquisition() {
        with(Fixture()) {
            migrate()
            success(ensure())
            val target = target()
            val locked = CountDownLatch(1)
            val finish = CountDownLatch(1)
            val trying = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val claim =
                    pool.submit<List<Map<String, Any?>>> {
                        success(
                            run(actor.copy(companyId = company)) {
                                val value =
                                    runtime.queryForList(
                                        "select * from claim_collaboration_legacy_runtime(?,?,?,?,clock_timestamp()+interval '30 seconds')",
                                        company,
                                        legacyDevice,
                                        account,
                                        parent,
                                    )
                                locked.countDown()
                                check(finish.await(5, TimeUnit.SECONDS))
                                value
                            }
                        )
                    }
                assertTrue(locked.await(5, TimeUnit.SECONDS))
                assertInstanceOf(
                    Result.Failed::class.java,
                    run {
                        runtime.queryForList(
                            "select * from collaboration_legacy_runtime for update nowait"
                        )
                    },
                )
                val freeze =
                    pool.submit<Int> {
                        trying.countDown()
                        success(freeze(target))
                    }
                assertTrue(trying.await(5, TimeUnit.SECONDS))
                assertFalse(freeze.isDone)
                finish.countDown()
                val token = claim.get(10, TimeUnit.SECONDS).single()["token"] as UUID
                assertEquals(1, freeze.get(10, TimeUnit.SECONDS))
                assertEquals("DRAINING", heads().single()["phase"])
                assertTrue(success(claim()).isEmpty())
                assertEquals(1, success(release(token)))
                assertTrue(success(claim()).isEmpty())
                assertInstanceOf(Result.Failed::class.java, freeze(target))
                assertInstanceOf(
                    Result.Failed::class.java,
                    run {
                        runtime.update(
                            "update collaboration_legacy_runtime set phase='LEGACY',version=0,frozen_at=null,target_space_id=null,target_device_id=null"
                        )
                    },
                )
            } finally {
                finish.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun frozenOwnershipAndSlotsSurviveOffboardingParentPurgeAndAnActualArchiveRestore() {
        with(Fixture()) {
            migrate()
            success(ensure())
            val target = target()
            val token = success(claim()).single()["token"] as UUID
            assertInstanceOf(Result.Failed::class.java, freeze(UUID.randomUUID()))
            val unrelatedTarget = UUID.randomUUID()
            success(run { insert(unrelatedTarget, origin = "SPACE") })
            assertInstanceOf(Result.Failed::class.java, freeze(unrelatedTarget))

            assertInstanceOf(
                Result.Failed::class.java,
                run(actor.copy(companyId = company)) {
                    runtime.update(
                        "update collaboration_legacy_runtime set phase='DRAINING',version=1,frozen_at=clock_timestamp(),target_space_id=?,target_device_id=?",
                        space,
                        target,
                    )
                },
            )
            success(freeze(target))
            val retained = heads()
            sql.update("update company_memberships set active=false where account_id=?", account)
            sql.update("update accounts set active=false where id=?", account)
            sql.update("delete from native_sessions where id=?", parent)
            assertEquals(retained, heads())
            assertEquals(1, success(release(token)))
            assertTrue(success(claim()).isEmpty())
            assertEquals(0, success(release(token)))
            assertTrue(leases(actor.copy(accountId = peer)).isEmpty())
            val restored = restore()
            assertEquals(
                retained,
                restored.queryForList(
                    "select * from collaboration_legacy_runtime order by company_id,device_id"
                ),
            )
            assertEquals(
                leases(),
                restored.queryForList(
                    "select * from collaboration_legacy_runtime_leases order by company_id,device_id,slot"
                ),
            )
            assertEquals(
                0,
                restored.queryForObject(
                    "select count(*) from pg_constraint where not convalidated",
                    Int::class.java,
                ),
            )
            assertEquals(
                1,
                restored.queryForObject(
                    "select count(*) from collaboration_provider_sessions where token_encrypted is not null",
                    Int::class.java,
                ),
            )
        }
    }

    @Test
    fun failedTransactionsCancellationAndChangedParentCannotLeaveAClaimOrFreeze() {
        with(Fixture()) {
            migrate()
            success(ensure())
            val target = target()
            val denied = Failure(FailureKind.CONFLICT, "fixture_failure")
            val current = actor.copy(companyId = company)
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(current) {
                    runtime.queryForList(
                        "select * from claim_collaboration_legacy_runtime(?,?,?,?,clock_timestamp()+interval '30 seconds')",
                        company,
                        legacyDevice,
                        account,
                        parent,
                    )
                    Result.Failed(denied)
                },
            )
            assertTrue(leases().isEmpty())
            assertThrows(CancellationException::class.java) {
                transactions.run<Unit>(current) {
                    runtime.queryForList(
                        "select * from claim_collaboration_legacy_runtime(?,?,?,?,clock_timestamp()+interval '30 seconds')",
                        company,
                        legacyDevice,
                        account,
                        parent,
                    )
                    throw CancellationException("owned fixture cancelled")
                }
            }
            assertTrue(leases().isEmpty())
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) {
                    runtime.update(
                        "update collaboration_legacy_runtime set phase='DRAINING',version=1,frozen_at=clock_timestamp(),target_space_id=?,target_device_id=?",
                        space,
                        target,
                    )
                    Result.Failed(denied)
                },
            )
            assertEquals("LEGACY", heads().single()["phase"])
            sql.update("update native_sessions set revoked_at=clock_timestamp() where id=?", parent)
            assertInstanceOf(Result.Failed::class.java, claim())
            assertTrue(leases().isEmpty())
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

        fun ensure(current: Actor = actor.copy(companyId = company)): Result<Int> =
            run(current) {
                runtime.update(
                    "insert into collaboration_legacy_runtime(company_id,device_id,account_id,native_session_id,phase,version,created_at) values(?,?,?,?,'LEGACY',0,clock_timestamp())",
                    company,
                    legacyDevice,
                    account,
                    parent,
                )
            }

        fun claim(
            until: Instant = now().plusSeconds(30),
            current: Actor = actor.copy(companyId = company),
        ): Result<List<Map<String, Any?>>> =
            run(current) {
                runtime.queryForList(
                    "select * from claim_collaboration_legacy_runtime(?,?,?,?,?::timestamptz)",
                    company,
                    legacyDevice,
                    account,
                    parent,
                    until.toString(),
                )
            }

        fun release(token: UUID, current: Actor = actor): Result<Int> =
            run(current) {
                runtime.update(
                    "update collaboration_legacy_runtime_leases set released_at=clock_timestamp() where company_id=? and device_id=? and token=? and released_at is null",
                    company,
                    legacyDevice,
                    token,
                )
            }

        fun freeze(target: UUID): Result<Int> = run {
            runtime.update(
                "update collaboration_legacy_runtime set phase='DRAINING',version=1,frozen_at=clock_timestamp(),target_space_id=?,target_device_id=? where company_id=? and device_id=?",
                space,
                target,
                company,
                legacyDevice,
            )
        }

        fun target(): UUID =
            UUID.randomUUID().also { id ->
                success(run { insert(id) })
                success(run { bind(id) })
            }

        fun heads(current: Actor = actor) =
            success(
                run(current) {
                    runtime.queryForList(
                        "select * from collaboration_legacy_runtime order by company_id,device_id"
                    )
                }
            )

        fun leases(current: Actor = actor) =
            success(
                run(current) {
                    runtime.queryForList(
                        "select * from collaboration_legacy_runtime_leases order by company_id,device_id,slot"
                    )
                }
            )

        fun restore(): JdbcTemplate {
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
                "/tmp/legacy-runtime.dump",
            )
            command("createdb", "-U", postgres.username, "legacy_runtime_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "legacy_runtime_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/legacy-runtime.dump",
            )
            return JdbcTemplate(
                DriverManagerDataSource(
                    postgres.jdbcUrl.substringBeforeLast('/') + "/legacy_runtime_restored",
                    postgres.username,
                    postgres.password,
                )
            )
        }
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
