package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.crypto.collaborationGatewayPurpose
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationGatewaySessionRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.schema.tables.CollaborationGatewaySessions.COLLABORATION_GATEWAY_SESSIONS as G
import java.nio.file.Path
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.*
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationGatewaySessionTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
        private val oldKey = Base64.getEncoder().encodeToString(ByteArray(32) { 11 })
        private val newKey = Base64.getEncoder().encodeToString(ByteArray(32) { 29 })
        private val oldKeys = parseIdentityKeyring("old", "old:$oldKey")
        private val currentKeys = parseIdentityKeyring("current", "old:$oldKey,current:$newKey")

        @BeforeAll
        @JvmStatic
        fun roles() {
            val jdbc =
                JdbcTemplate(
                    DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                )
            jdbc.execute("create role gateway_fixture login password 'gateway-fixture-only'")
            jdbc.execute(
                "create role gateway_worker_fixture login password 'gateway-worker-fixture-only'"
            )
        }
    }

    private lateinit var name: String
    private lateinit var admin: DriverManagerDataSource
    private lateinit var sql: JdbcTemplate
    private lateinit var runtime: DriverManagerDataSource
    private lateinit var worker: DriverManagerDataSource

    @BeforeEach
    fun database() {
        name = "gateway_" + UUID.randomUUID().toString().replace("-", "")
        command("createdb", "-U", postgres.username, name)
        admin = DriverManagerDataSource(url(name), postgres.username, postgres.password)
        sql = JdbcTemplate(admin)
        Flyway.configure().dataSource(admin).load().migrate()
        sql.execute("grant hris_worker_capability to gateway_worker_fixture")
        sql.execute("grant usage on schema public to gateway_fixture,gateway_worker_fixture")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to gateway_fixture,gateway_worker_fixture"
        )
        runtime = DriverManagerDataSource(url(name), "gateway_fixture", "gateway-fixture-only")
        worker =
            DriverManagerDataSource(
                url(name),
                "gateway_worker_fixture",
                "gateway-worker-fixture-only",
            )
    }

    @Test
    fun tokenLookupOnlyIdentifiesOneScopedHeadAndItsCapabilityDoesNotEscapeTheTransaction() {
        val f = Fixture()
        val tokens =
            success(f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        assertTrue(validCollaborationGatewayAccess(tokens.accessToken))
        assertTrue(validCollaborationGatewayRefresh(tokens.refreshToken))
        assertFalse(validCollaborationGatewayAccess(tokens.refreshToken))
        assertFalse(validCollaborationGatewayRefresh(tokens.accessToken))
        assertTrue(tokens.toString().contains("redacted"))
        assertFalse(f.raw().replayEncrypted.contains(tokens.accessToken))
        assertFalse(f.raw().replayEncrypted.contains(tokens.refreshToken))
        assertEquals(f.session(), success(f.tx { f.current.find(f.company, f.account, f.device) }))
        val foreign = f.actor.copy(accountId = UUID.randomUUID())
        assertNull(
            success(f.transactions.run(foreign) { f.current.find(f.company, f.account, f.device) })
        )
        assertNull(
            success(
                f.transactions.run(f.actor.copy(companyId = UUID.randomUUID())) {
                    f.current.find(f.company, f.account, f.device)
                }
            )
        )
        assertNull(success(f.tx { f.current.replayBootstrap(f.session(), UUID.randomUUID()) }))
        val replay =
            requireNotNull(success(f.tx { f.current.replayBootstrap(f.session(), f.operation) }))
        assertEquals(tokens.accessToken, replay.accessToken)
        assertEquals(tokens.refreshToken, replay.refreshToken)

        val connection = SingleConnectionDataSource(runtime.connection, true)
        try {
            val tx = transactions(connection)
            val repository = repository(connection)
            val anonymous = f.actor.copy(accountId = UUID(0, 0), companyId = null)
            assertEquals(
                f.session(),
                success(tx.run(anonymous) { repository.findAccess(tokens.accessToken) }),
            )
            assertFalse(
                requireNotNull(
                        success(tx.run(anonymous) { repository.findRefresh(tokens.refreshToken) })
                    )
                    .previous
            )
            assertNull(
                success(tx.run(anonymous) { repository.find(f.company, f.account, f.device) })
            )
            assertTrue(
                JdbcTemplate(connection)
                    .queryForObject(
                        "select current_setting('app.collaboration_gateway_lookup',true)",
                        String::class.java,
                    )
                    .isNullOrBlank()
            )
            assertEquals(
                0,
                JdbcTemplate(connection)
                    .queryForObject(
                        "select count(*) from collaboration_gateway_sessions",
                        Int::class.java,
                    ),
            )
        } finally {
            connection.destroy()
        }
    }

    @Test
    fun concurrentRefreshAndLostResponseReplayKeepOneBoundedHeadWithoutRecreatingADevice() {
        val f = Fixture()
        val tokens =
            success(f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        val next =
            f.session()
                .copy(
                    version = 1,
                    rotatedAt = f.at.plusSeconds(1),
                    accessExpiresAt = f.at.plusSeconds(301),
                )
        val barrier = CyclicBarrier(2)
        val source =
            object : CollaborationGatewaySessionDataSource by f.source {
                override fun find(companyId: UUID, accountId: UUID, deviceId: UUID, lock: Boolean) =
                    f.source.find(companyId, accountId, deviceId, lock).also {
                        barrier.await(5, TimeUnit.SECONDS)
                    }
            }
        val racing =
            EncryptedCollaborationGatewaySessionRepository(
                source,
                JceIdentityTokenDataSource(currentKeys),
            )
        val pool = Executors.newFixedThreadPool(2)
        val results =
            try {
                List(2) {
                        pool.submit<Result<CollaborationGatewayTokens>> {
                            f.tx {
                                racing.rotateRefresh(
                                    next,
                                    tokens.refreshToken,
                                    f.at.plusSeconds(31),
                                )
                            }
                        }
                    }
                    .map { it.get(10, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        assertEquals(1, results.count { it is Result.Success })
        assertEquals(
            1,
            results.count {
                it is Result.Failed && it.failure.code == "collaboration_gateway_changed"
            },
        )
        val winner = (results.single { it is Result.Success } as Result.Success).value
        val replay =
            requireNotNull(success(f.tx { f.current.replayRefresh(next, tokens.refreshToken) }))
        assertEquals(winner.accessToken, replay.accessToken)
        assertEquals(winner.refreshToken, replay.refreshToken)
        assertNull(success(f.tx { f.current.findAccess(tokens.accessToken) }))
        assertTrue(
            requireNotNull(success(f.tx { f.current.findRefresh(tokens.refreshToken) })).previous
        )
        assertNull(success(f.tx { f.current.replayBootstrap(next, f.operation) }))
        val third =
            next.copy(
                version = 2,
                rotatedAt = f.at.plusSeconds(2),
                accessExpiresAt = f.at.plusSeconds(302),
            )
        success(f.tx { f.current.rotateRefresh(third, winner.refreshToken, f.at.plusSeconds(32)) })
        assertNull(success(f.tx { f.current.findRefresh(tokens.refreshToken) }))
        assertNull(success(f.tx { f.current.replayRefresh(third, tokens.refreshToken) }))
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_gateway_sessions",
                Int::class.java,
            ),
        )
        assertEquals(
            1,
            sql.queryForObject("select count(*) from collaboration_devices", Int::class.java),
        )
    }

    @Test
    fun keyRotationAndBothReplayKindsSurviveActualRestoreAndCanonicalRecoveryProjection() {
        val f = Fixture()
        val retained = Fixture()
        val retainedTokens =
            success(
                retained.tx {
                    retained.old.create(
                        retained.session(),
                        retained.operation,
                        retained.at.plusSeconds(30),
                    )
                }
            )
        val original =
            success(f.tx { f.old.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        val next =
            f.session()
                .copy(
                    version = 1,
                    rotatedAt = f.at.plusSeconds(1),
                    accessExpiresAt = f.at.plusSeconds(301),
                )
        val rotated =
            success(
                f.tx { f.current.rotateRefresh(next, original.refreshToken, f.at.plusSeconds(31)) }
            )
        assertTrue(f.tx { f.old.replayRefresh(next, original.refreshToken) } is Result.Failed)
        val restored = "restored_" + name
        command("pg_dump", "-U", postgres.username, "-d", name, "-Fc", "-f", "/tmp/gateway.dump")
        command("createdb", "-U", postgres.username, restored)
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            restored,
            "--exit-on-error",
            "/tmp/gateway.dump",
        )
        val restoredSource =
            DriverManagerDataSource(url(restored), "gateway_fixture", "gateway-fixture-only")
        val tx = transactions(restoredSource)
        val store = repository(restoredSource)
        val replay =
            requireNotNull(
                success(tx.run(f.actor) { store.replayRefresh(next, original.refreshToken) })
            )
        assertEquals(rotated.accessToken, replay.accessToken)
        assertEquals(rotated.refreshToken, replay.refreshToken)
        assertEquals(
            retainedTokens.accessToken,
            success(
                    tx.run(retained.actor) {
                        store.replayBootstrap(retained.session(), retained.operation)
                    }
                )
                ?.accessToken,
        )
        assertNull(
            success(
                tx.run(f.actor.copy(companyId = UUID.randomUUID())) {
                    store.find(f.company, f.account, f.device)
                }
            )
        )
        val root = Path.of(System.getProperty("user.dir")).resolve("../../..").normalize()
        val queryProcess =
            ProcessBuilder(
                    "python3",
                    "-c",
                    "import sys;sys.path.insert(0,'tool');import recovery_set;print(recovery_set.envelope_query(109))",
                )
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start()
        val query =
            try {
                assertTrue(queryProcess.waitFor(5, TimeUnit.SECONDS))
                assertEquals(0, queryProcess.exitValue())
                queryProcess.inputStream.bufferedReader().use { it.readText() }.trim()
            } finally {
                if (queryProcess.isAlive) queryProcess.destroyForcibly()
            }
        val projection =
            postgres.execInContainer(
                "psql",
                "-X",
                "-q",
                "-U",
                postgres.username,
                "-d",
                restored,
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                query,
            )
        assertEquals(0, projection.exitCode, projection.stderr)
        val rows =
            projection.stdout
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { it.split('\t') }
                .toList()
        assertEquals(2, rows.size)
        assertEquals(setOf("old", "current"), rows.map { it[1].substringBefore('.') }.toSet())
        assertEquals(
            setOf(
                collaborationGatewayPurpose(f.raw()),
                collaborationGatewayPurpose(retained.raw()),
            ),
            rows.map { it[0] }.toSet(),
        )
        assertEquals(
            setOf(
                "${rotated.accessToken}\n${rotated.refreshToken}",
                "${retainedTokens.accessToken}\n${retainedTokens.refreshToken}",
            ),
            rows.map { decryptIdentityEnvelope(currentKeys, it[0], it[1]) }.toSet(),
        )
    }

    @Test
    fun deadlineTamperingAndFailedBusinessResultsCannotPublishAnotherCredentialGeneration() {
        val f = Fixture()
        val first =
            success(f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        val encrypted = f.raw().replayEncrypted
        val next =
            f.session()
                .copy(
                    version = 1,
                    rotatedAt = f.at.plusSeconds(1),
                    accessExpiresAt = f.at.plusSeconds(301),
                )
        assertTrue(
            f.tx {
                f.current.rotateRefresh(next, first.refreshToken, f.at.plusSeconds(31)).flatMap {
                    Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
                }
            } is Result.Failed
        )
        assertEquals(encrypted, f.raw().replayEncrypted)
        assertEquals(0L, f.raw().version)
        assertNotNull(success(f.tx { f.current.findAccess(first.accessToken) }))
        TransactionTemplate(JdbcTransactionManager(admin)).executeWithoutResult {
            sql.execute(
                "alter table collaboration_gateway_sessions disable trigger protect_collaboration_gateway_session"
            )
            sql.update(
                "update collaboration_gateway_sessions set replay_until=replay_until+interval '1 microsecond' where device_id=?",
                f.device,
            )
            sql.execute(
                "alter table collaboration_gateway_sessions enable trigger protect_collaboration_gateway_session"
            )
        }
        assertTrue(f.tx { f.current.replayBootstrap(f.session(), f.operation) } is Result.Failed)
    }

    @Test
    fun aNewBootstrapUpdatesBuildWithoutReplacingDeviceAndInvalidatesAnOlderCredentialPair() {
        val f = Fixture()
        val first =
            success(f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        val next =
            f.session()
                .copy(
                    version = 1,
                    clientBuild = 2,
                    rotatedAt = f.at.plusSeconds(1),
                    accessExpiresAt = f.at.plusSeconds(301),
                )
        val operation = UUID.randomUUID()
        val rotated =
            success(f.tx { f.current.rotateBootstrap(next, operation, f.at.plusSeconds(31)) })
        val replay = requireNotNull(success(f.tx { f.current.replayBootstrap(next, operation) }))
        assertEquals(rotated.accessToken, replay.accessToken)
        assertEquals(rotated.refreshToken, replay.refreshToken)
        assertEquals(1L, replay.version)
        assertEquals(2, f.raw().clientBuild)
        assertEquals(f.id, f.raw().id)
        assertEquals(f.device, f.raw().deviceId)
        assertNull(success(f.tx { f.current.findAccess(first.accessToken) }))
        assertNull(success(f.tx { f.current.findRefresh(first.refreshToken) }))
        assertNull(success(f.tx { f.current.replayBootstrap(next, f.operation) }))
        assertNull(success(f.tx { f.current.replayBootstrap(f.session(), f.operation) }))
        assertTrue(
            f.tx { f.current.rotateBootstrap(next, f.operation, f.at.plusSeconds(31)) }
                is Result.Failed
        )
        assertEquals(
            rotated.accessToken,
            requireNotNull(success(f.tx { f.current.replayBootstrap(next, operation) })).accessToken,
        )
    }

    @Test
    fun expiredCacheCleanupIsBoundedAndCannotChangeLiveCredentialsOrSessionFacts() {
        val first = Fixture(Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS))
        val second = Fixture(first.at)
        val live = Fixture()
        val tokens =
            success(
                first.tx {
                    first.current.create(first.session(), first.operation, first.at.plusSeconds(30))
                }
            )
        success(
            second.tx {
                second.current.create(second.session(), second.operation, second.at.plusSeconds(30))
            }
        )
        success(
            live.tx { live.current.create(live.session(), live.operation, live.at.plusSeconds(30)) }
        )
        val tx = transactions(worker)
        val store = repository(worker)
        val scope = first.actor.copy(accountId = UUID(0, 0), companyId = null)
        assertNull(success(tx.run(scope) { store.find(live.company, live.account, live.device) }))
        assertTrue(
            tx.run(scope) {
                try {
                    JdbcTemplate(worker)
                        .update(
                            "update collaboration_gateway_sessions set client_build=99,replay_kind=null,bootstrap_operation_id=null,previous_refresh_hash=null,replay_until=null,replay_encrypted=null where device_id=?",
                            first.device,
                        )
                    Result.Success(Unit)
                } catch (_: org.springframework.dao.DataAccessException) {
                    Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))
                }
            } is Result.Failed
        )
        assertEquals(1, success(tx.run(scope) { store.clearExpiredReplays(Instant.now(), 1) }))
        assertEquals(1, success(tx.run(scope) { store.clearExpiredReplays(Instant.now(), 100) }))
        assertEquals(0, success(tx.run(scope) { store.clearExpiredReplays(Instant.now(), 100) }))
        assertNull(first.raw().replayEncrypted)
        assertNull(second.raw().replayEncrypted)
        assertEquals(first.at.plusSeconds(30), first.raw().replayUntil.toInstant())
        assertNull(first.raw().bootstrapOperationId)
        assertNull(first.raw().previousRefreshHash)
        assertNull(first.raw().replayKind)
        assertNotNull(live.raw().replayEncrypted)
        assertEquals(
            0,
            success(
                live.tx { live.current.clearExpiredReplays(Instant.now().plusSeconds(3600), 100) }
            ),
        )
        assertNotNull(live.raw().replayEncrypted)
        assertEquals(0L, first.raw().version)
        assertNotNull(success(first.tx { first.current.findAccess(tokens.accessToken) }))
        assertNotNull(success(first.tx { first.current.findRefresh(tokens.refreshToken) }))
        assertNull(
            success(first.tx { first.current.replayBootstrap(first.session(), first.operation) })
        )
    }

    @Test
    fun expiryCleanupSkipsACompetingLockedHeadAndFinishesAfterItsOwnerReleasesIt() {
        val f = Fixture(Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS))
        success(f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val held =
                pool.submit<Result<Unit>> {
                    f.tx {
                        f.current.find(f.company, f.account, f.device, lock = true).flatMap {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                            Result.Success(Unit)
                        }
                    }
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val tx = transactions(worker)
            val store = repository(worker)
            val scope = f.actor.copy(accountId = UUID(0, 0), companyId = null)
            assertEquals(
                0,
                success(tx.run(scope) { store.clearExpiredReplays(Instant.now(), 100) }),
            )
            release.countDown()
            assertEquals(Result.Success(Unit), held.get(5, TimeUnit.SECONDS))
            assertEquals(
                1,
                success(tx.run(scope) { store.clearExpiredReplays(Instant.now(), 100) }),
            )
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun scopeLifetimeAndReplayConstraintsRejectUnboundOrMalformedIssuance() {
        val f = Fixture()
        for (session in
            listOf(
                f.session().copy(nativeSessionId = UUID.randomUUID()),
                f.session().copy(platform = CollaborationPlatform.IOS),
                f.session().copy(expiresAt = f.at.plusSeconds(172800)),
                f.session().copy(accessExpiresAt = f.at.plusSeconds(901)),
                f.session().copy(version = 1),
            )) {
            assertTrue(
                f.tx { f.current.create(session, f.operation, f.at.plusSeconds(30)) }
                    is Result.Failed
            )
        }
        assertTrue(
            f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(61)) }
                is Result.Failed
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from collaboration_gateway_sessions",
                Int::class.java,
            ),
        )
        success(f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            sql.update(
                "update collaboration_gateway_sessions set replay_kind=null where device_id=?",
                f.device,
            )
        }
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            sql.update("delete from collaboration_gateway_sessions where device_id=?", f.device)
        }
    }

    @Test
    fun terminalRetirementReservesItsLastRevisionAndSurvivesNativeParentPurge() {
        val f = Fixture()
        val first =
            success(f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) })
        assertTrue(
            f.tx { f.current.retire(f.company, f.account, f.device, 0, Instant.now()) }
                is Result.Failed
        )
        TransactionTemplate(JdbcTransactionManager(admin)).executeWithoutResult {
            sql.execute(
                "alter table collaboration_gateway_sessions disable trigger protect_collaboration_gateway_session"
            )
            sql.update(
                "update collaboration_gateway_sessions set version=999999 where device_id=?",
                f.device,
            )
            sql.execute(
                "alter table collaboration_gateway_sessions enable trigger protect_collaboration_gateway_session"
            )
        }
        val exhausted =
            f.session()
                .copy(
                    version = 1000000,
                    rotatedAt = f.at.plusSeconds(1),
                    accessExpiresAt = f.at.plusSeconds(301),
                )
        assertTrue(
            f.tx { f.current.rotateRefresh(exhausted, first.refreshToken, f.at.plusSeconds(31)) }
                is Result.Failed
        )
        sql.update(
            "update collaboration_devices set state='REVOKED',version=version+1,revoked_at=now(),updated_at=now() where id=?",
            f.device,
        )
        success(f.tx { f.current.retire(f.company, f.account, f.device, 999999, Instant.now()) })
        assertEquals(1000000L, f.raw().version)
        assertNull(f.raw().accessHash)
        assertNull(f.raw().refreshHash)
        assertNull(f.raw().replayEncrypted)
        assertNull(success(f.tx { f.current.findAccess(first.accessToken) }))
        assertTrue(
            f.tx { f.current.retire(f.company, f.account, f.device, 999999, Instant.now()) }
                is Result.Failed
        )
        assertTrue(
            f.tx { f.current.create(f.session(), f.operation, f.at.plusSeconds(30)) }
                is Result.Failed
        )
        sql.update("delete from native_sessions where id=?", f.parent)
        assertEquals(
            1000000L,
            success(f.tx { f.current.find(f.company, f.account, f.device) })?.version,
        )
    }

    private inner class Fixture(val at: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)) {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val device = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val actor = Actor(account, company, emptySet(), at, UUID.randomUUID())
        val transactions = transactions(runtime)
        val source = source(runtime)
        val current = repository(runtime)
        val old = repository(runtime, oldKeys)

        init {
            sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Gateway fixture','UTC')",
                company,
                company.toString().take(12),
            )
            sql.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional gateway employee')",
                account,
                "$account@example.test",
            )
            val before = at.minusSeconds(120).atOffset(ZoneOffset.UTC)
            val hash = parent.toString().replace("-", "").repeat(2)
            sql.update(
                """insert into native_sessions(id,account_id,refresh_hash,access_hash,authenticated_at,expires_at,access_expires_at,created_at,rotated_at)
                values(?,?,?,?,?,?,?,?,?)""",
                parent,
                account,
                hash,
                hash,
                before,
                at.plusSeconds(86400).atOffset(ZoneOffset.UTC),
                at.plusSeconds(900).atOffset(ZoneOffset.UTC),
                before,
                before,
            )
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional gateway device','PROVISIONING',0,?,?)",
                company,
                device,
                account,
                before,
                before,
            )
            sql.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?)",
                company,
                device,
                account,
                parent,
                before,
            )
            sql.update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=? where id=?",
                before,
                device,
            )
        }

        fun session() =
            CollaborationGatewaySession(
                id,
                company,
                account,
                device,
                parent,
                CollaborationPlatform.ANDROID,
                1,
                0,
                at,
                at,
                at.plusSeconds(86400),
                at.plusSeconds(300),
            )

        fun <T> tx(action: () -> Result<T>): Result<T> = transactions.run(actor, action)

        fun raw() =
            requireNotNull(
                DSL.using(admin, SQLDialect.POSTGRES)
                    .selectFrom(G)
                    .where(G.COMPANY_ID.eq(company), G.DEVICE_ID.eq(device))
                    .fetchOne()
            )
    }

    private fun source(data: DataSource) =
        PostgresCollaborationGatewaySessionDataSource(
            DSL.using(TransactionAwareDataSourceProxy(data), SQLDialect.POSTGRES)
        )

    private fun repository(data: DataSource, keys: IdentityKeyring = currentKeys) =
        EncryptedCollaborationGatewaySessionRepository(
            source(data),
            JceIdentityTokenDataSource(keys),
        )

    private fun transactions(data: DataSource) =
        PostgresTransactionRunner(JdbcTransactionManager(data), JdbcTemplate(data))

    private fun url(database: String) = postgres.jdbcUrl.substringBeforeLast('/') + "/" + database

    private fun command(vararg args: String) {
        val result = postgres.execInContainer(*args)
        assertEquals(0, result.exitCode, result.stderr)
    }

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }
}
