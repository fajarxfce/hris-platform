package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.crypto.collaborationProviderPurpose
import dev.fajar.hris.collaboration.data.crypto.collaborationSpaceProviderPurpose
import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceProviderSessionDataSource
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSpaceProviderSessionRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
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
class CollaborationSpaceProviderStorageTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    private val oldKey = Base64.getEncoder().encodeToString(ByteArray(32) { 11 })
    private val newKey = Base64.getEncoder().encodeToString(ByteArray(32) { 29 })
    private val oldKeys = parseIdentityKeyring("old", "old:$oldKey")
    private val currentKeys = parseIdentityKeyring("current", "old:$oldKey,current:$newKey")

    @Test
    fun actualEncryptedRowsAndProductionRecoveryProjectionSurviveArchiveUnderCurrentAndRetainedKeys() {
        val db = Database()
        val f = db.Fixture()
        val retained = db.Fixture()
        val original = f.session()
        success(f.run { f.old.create(original) })
        success(retained.run { retained.old.create(retained.session("fictional-retained-token")) })
        val next = f.session("fictional-current-token", 1)
        success(f.run { f.current.rotate(next, 0) })
        assertEquals(next, success(f.run { f.current.credential(next.state) }))
        assertInstanceOf(Result.Failed::class.java, f.run { f.old.credential(next.state) })
        assertInstanceOf(Result.Failed::class.java, f.run { f.current.credential(original.state) })
        val legacy = f.legacyEnvelope()
        val before =
            db.owner.queryForList(
                "select * from collaboration_space_provider_sessions order by device_id"
            )
        val oldRows =
            db.owner.queryForList(
                "select * from collaboration_provider_sessions order by device_id"
            )
        assertFalse(before.toString().contains(next.accessToken))
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/group-provider.dump",
        )
        command("createdb", "-U", postgres.username, "group_provider_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "group_provider_restored",
            "--single-transaction",
            "--exit-on-error",
            "/tmp/group-provider.dump",
        )
        val restoredOwner =
            JdbcTemplate(
                DriverManagerDataSource(
                    url("group_provider_restored"),
                    postgres.username,
                    postgres.password,
                )
            )
        assertEquals(
            before,
            restoredOwner.queryForList(
                "select * from collaboration_space_provider_sessions order by device_id"
            ),
        )
        assertEquals(
            oldRows,
            restoredOwner.queryForList(
                "select * from collaboration_provider_sessions order by device_id"
            ),
        )
        val runtime = runtime("group_provider_restored")
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val restored = repository(runtime, currentKeys)
        assertEquals(next, success(transactions.run(f.actor) { restored.credential(next.state) }))
        val retainedState = retained.session("fictional-retained-token").state
        assertEquals(
            "fictional-retained-token",
            success(transactions.run(retained.actor) { restored.credential(retainedState) })
                .accessToken,
        )
        assertEquals(
            Result.Success(null),
            transactions.run(f.actor.copy(accountId = UUID.randomUUID())) {
                restored.metadata(db.space, f.account, f.device)
            },
        )
        val projection =
            postgres.execInContainer(
                "psql",
                "-X",
                "-q",
                "-U",
                postgres.username,
                "-d",
                "group_provider_restored",
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                productionProjection(),
            )
        assertEquals(0, projection.exitCode, projection.stderr)
        val rows =
            projection.stdout
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { it.split('\t') }
                .toList()
        assertEquals(3, rows.size)
        assertEquals(
            setOf(
                collaborationSpaceProviderPurpose(next.state),
                collaborationSpaceProviderPurpose(retainedState),
                legacy.first,
            ),
            rows.map { it[0] }.toSet(),
        )
        assertEquals(
            setOf("fictional-current-token", "fictional-retained-token", legacy.second),
            rows.map { decryptIdentityEnvelope(currentKeys, it[0], it[1]) }.toSet(),
        )
        val changed = rows.first { it[0] == collaborationSpaceProviderPurpose(next.state) }
        assertThrows(Exception::class.java) {
            decryptIdentityEnvelope(
                currentKeys,
                collaborationSpaceProviderPurpose(
                    next.state.copy(expiresAt = next.state.expiresAt.plusNanos(1000))
                ),
                changed[1],
            )
        }
        assertThrows(Exception::class.java) {
            decryptIdentityEnvelope(
                parseIdentityKeyring("current", "current:$newKey"),
                rows.first { it[0] == collaborationSpaceProviderPurpose(retainedState) }[0],
                rows.first { it[0] == collaborationSpaceProviderPurpose(retainedState) }[1],
            )
        }
    }

    @Test
    fun concurrentRotationsHaveOneWinnerAndFailedOrCancelledTransactionsLeaveNoCredential() {
        val db = Database()
        val f = db.Fixture()
        success(f.run { f.current.create(f.session()) })
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures =
                (1..2).map { index ->
                    pool.submit<Pair<CollaborationSpaceProviderSession, Result<Unit>>> {
                        val candidate = f.session("fictional-candidate-$index", 1)
                        barrier.await(10, TimeUnit.SECONDS)
                        candidate to f.run { f.current.rotate(candidate, 0) }
                    }
                }
            val results = futures.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.second is Result.Success })
            val loser = results.single { it.second is Result.Failed }.second as Result.Failed
            assertEquals("collaboration_provider_generation_changed", loser.failure.code)
            val winner = results.single { it.second is Result.Success }.first
            assertEquals(
                winner.state,
                success(f.run { f.current.metadata(db.space, f.account, f.device) }),
            )
            assertEquals(winner, success(f.run { f.current.credential(winner.state) }))
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        val failed = db.Fixture()
        assertInstanceOf(
            Result.Failed::class.java,
            failed.run {
                success(failed.current.create(failed.session()))
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_failure"))
            },
        )
        assertEquals(
            Result.Success(null),
            failed.run { failed.current.metadata(db.space, failed.account, failed.device) },
        )
        for (error in listOf(CancellationException("fixture"), InterruptedException("fixture"))) {
            assertThrows(error.javaClass) {
                failed.run<Unit> {
                    success(failed.current.create(failed.session()))
                    throw error
                }
            }
            assertEquals(
                Result.Success(null),
                failed.run { failed.current.metadata(db.space, failed.account, failed.device) },
            )
        }
        success(failed.run { failed.current.create(failed.session()) })
    }

    @Test
    fun ownedMetadataAndRetirementNeedNoKeyAndRemainDistinctFromAbsenceAfterParentPurge() {
        val db = Database()
        val f = db.Fixture()
        assertEquals(
            Result.Success(null),
            f.run { f.current.metadata(db.space, f.account, f.device) },
        )
        success(f.run { f.current.create(f.session()) })
        // This repository lacks the encryption key used above. Metadata/cleanup must not decrypt.
        assertEquals(
            f.session().state,
            success(f.run { f.old.metadata(db.space, f.account, f.device) }),
        )
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = f.company),
            )) {
            assertEquals(
                Result.Success(null),
                f.run(actor) { f.current.metadata(db.space, f.account, f.device) },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.run(actor) { f.current.credential(f.session().state) },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.run(actor) { f.current.retire(db.space, f.account, f.device, 0, f.at) },
            )
        }
        db.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        db.owner.update("delete from native_sessions where id=?", f.parent)
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.current.rotate(f.session("new", 1), 0) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.old.retire(db.space, f.account, f.device, 0, f.at) },
        )
        db.owner.update(
            "update collaboration_space_devices set state='REVOKED',version=1,revoked_at=updated_at where id=?",
            f.device,
        )
        success(f.run { f.old.retire(db.space, f.account, f.device, 0, f.at) })
        val retired =
            requireNotNull(success(f.run { f.old.metadata(db.space, f.account, f.device) }))
        assertEquals(f.session().state.copy(generation = 1, clearedAt = f.at), retired)
        assertInstanceOf(Result.Failed::class.java, f.run { f.current.credential(retired) })
        assertInstanceOf(Result.Failed::class.java, f.run { f.current.create(f.session()) })
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.old.retire(db.space, f.account, f.device, 1, f.at) },
        )
    }

    private inner class Database {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val owner = JdbcTemplate(source)
        val space: UUID

        init {
            Flyway.configure().dataSource(source).target("121").load().migrate()
            space =
                requireNotNull(
                    owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
                )
            owner.execute(
                "create role group_provider_fixture login password 'isolated-fixture-only'"
            )
            owner.execute("grant usage on schema public to group_provider_fixture")
            owner.execute(
                "grant select,insert,update,delete on all tables in schema public to group_provider_fixture"
            )
        }

        inner class Fixture {
            val account = UUID.randomUUID()
            val company = UUID.randomUUID()
            val parent = UUID.randomUUID()
            val device = UUID.randomUUID()
            val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val actor =
                Actor(
                    account,
                    null,
                    emptySet(),
                    at.minusSeconds(60),
                    UUID.randomUUID(),
                    credentialVersion = 0,
                )
            val user = "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test"
            val runtime = runtime(postgres.databaseName)
            val transactions =
                PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
            val old = repository(runtime, oldKeys)
            val current = repository(runtime, currentKeys)

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
                    "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                    parent,
                    account,
                    compact(parent).repeat(2),
                    actor.authenticatedAt.toString(),
                    at.plusSeconds(3600).toString(),
                    at.plusSeconds(900).toString(),
                    at.minusSeconds(60).toString(),
                    at.minusSeconds(30).toString(),
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

            fun session(
                token: String = "fictional-first-token",
                generation: Long = 0,
            ): CollaborationSpaceProviderSession =
                CollaborationSpaceProviderSession(
                    CollaborationSpaceProviderState(
                        space,
                        account,
                        device,
                        parent,
                        user,
                        "HRISG_${compact(device)}",
                        generation,
                        at,
                        at.plusSeconds(generation),
                        at.plusSeconds(500 + generation),
                    ),
                    token,
                )

            fun <T> run(currentActor: Actor = actor, body: () -> Result<T>): Result<T> =
                transactions.run(currentActor, body)

            fun legacyEnvelope(): Pair<String, String> {
                val oldDevice = UUID.randomUUID()
                val token = "fictional-old-company-token"
                val expiry = at.plusSeconds(500)
                val purpose = collaborationProviderPurpose(company, account, oldDevice, 0, expiry)
                val encrypted = JceIdentityTokenDataSource(oldKeys).encrypt(purpose, token)
                owner.update(
                    "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                    company,
                    oldDevice,
                    account,
                    at.toString(),
                    at.toString(),
                )
                owner.update(
                    "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,?,?::timestamptz,?::timestamptz,?::timestamptz)",
                    company,
                    oldDevice,
                    account,
                    "@hris_${compact(company)}_${compact(account)}:matrix.example.test",
                    "HRIS_${compact(oldDevice)}",
                    encrypted,
                    at.toString(),
                    at.toString(),
                    expiry.toString(),
                )
                return purpose to token
            }
        }
    }

    private fun url(database: String) = postgres.jdbcUrl.substringBeforeLast('/') + "/" + database

    private fun runtime(database: String) =
        DriverManagerDataSource(url(database), "group_provider_fixture", "isolated-fixture-only")

    private fun repository(source: DriverManagerDataSource, keys: IdentityKeyring) =
        EncryptedCollaborationSpaceProviderSessionRepository(
            PostgresCollaborationSpaceProviderSessionDataSource(
                DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
            ),
            JceIdentityTokenDataSource(keys),
        )

    private fun command(vararg args: String) {
        val result = postgres.execInContainer(*args)
        assertEquals(0, result.exitCode, result.stderr)
    }

    private fun productionProjection(): String {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("tool/recovery_set.py")) }
        val process =
            ProcessBuilder(
                    "python3",
                    "-c",
                    "import sys;sys.path.insert(0,'tool');import recovery_set;print(recovery_set.envelope_query(121))",
                )
                .directory(root.toFile())
                .start()
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            return process.inputStream.bufferedReader().use { it.readText() }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            }
            process.inputStream.close()
            process.errorStream.close()
            process.outputStream.close()
        }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
