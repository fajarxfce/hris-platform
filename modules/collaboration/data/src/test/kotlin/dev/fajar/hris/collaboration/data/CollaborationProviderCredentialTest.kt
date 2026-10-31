package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.crypto.collaborationProviderPurpose
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationProviderSessionRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationProviderSession
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationProviderCredentialTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
        private lateinit var admin: DriverManagerDataSource
        private lateinit var sql: JdbcTemplate
        private lateinit var databaseName: String
        private val oldKey = Base64.getEncoder().encodeToString(ByteArray(32) { 11 })
        private val newKey = Base64.getEncoder().encodeToString(ByteArray(32) { 29 })
        private val oldKeys = parseIdentityKeyring("old", "old:$oldKey")
        private val currentKeys = parseIdentityKeyring("current", "old:$oldKey,current:$newKey")

        @BeforeAll
        @JvmStatic
        fun prepare() {
            JdbcTemplate(
                    DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                )
                .execute("create role provider_fixture login password 'provider-fixture-only'")
        }
    }

    @BeforeEach
    fun prepareDatabase() {
        // Corruption/revocation scenarios cannot contaminate the complete recovery projection.
        databaseName = "provider_case_" + UUID.randomUUID().toString().replace("-", "")
        command("createdb", "-U", postgres.username, databaseName)
        admin =
            DriverManagerDataSource(
                postgres.jdbcUrl.substringBeforeLast('/') + "/" + databaseName,
                postgres.username,
                postgres.password,
            )
        sql = JdbcTemplate(admin)
        Flyway.configure().dataSource(admin).target("105").load().migrate()
        Flyway.configure().dataSource(admin).target("106").load().migrate()
        sql.execute("grant usage on schema public to provider_fixture")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to provider_fixture"
        )
    }

    @Test
    fun scopedEncryptionAndKeyRotationSurviveAnActualDatabaseRestore() {
        val f = Fixture()
        val retained = Fixture()
        assertEquals(
            Result.Success(Unit),
            retained.transaction {
                retained.old.create(retained.session(token = "fictional-retained-old-key-token"))
            },
        )
        val first = f.session()
        assertEquals(Result.Success(Unit), f.transaction { f.old.create(first) })
        val encrypted = f.ciphertext()
        assertFalse(encrypted.contains(first.accessToken))
        assertEquals(
            first.accessToken,
            success(f.transaction { f.current.find(f.company, f.account, f.device) })?.accessToken,
        )
        assertEquals(
            Result.Success(null),
            f.transactions.run(f.actor.copy(accountId = UUID.randomUUID())) {
                f.current.find(f.company, f.account, f.device)
            },
        )
        assertEquals(
            Result.Success(null),
            f.transactions.run(f.actor.copy(companyId = UUID.randomUUID())) {
                f.current.find(f.company, f.account, f.device)
            },
        )
        val next = f.session(generation = 1, token = "fictional-rotated-provider-token")
        assertEquals(Result.Success(Unit), f.transaction { f.current.rotate(next, 0) })
        assertEquals(
            "collaboration_provider_generation_changed",
            failure(f.transaction { f.current.rotate(next, 0) }),
        )
        assertEquals(
            "identity_key_unavailable",
            failure(f.transaction { f.old.find(f.company, f.account, f.device) }),
        )
        assertNotEquals(encrypted, f.ciphertext())

        val restored = "provider_restored_" + UUID.randomUUID().toString().replace("-", "")
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            databaseName,
            "-Fc",
            "-f",
            "/tmp/provider-credentials.dump",
        )
        command("createdb", "-U", postgres.username, restored)
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            restored,
            "--exit-on-error",
            "/tmp/provider-credentials.dump",
        )
        val source = runtime(restored)
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source))
        val store = repository(source, currentKeys)
        val found =
            success(transactions.run(f.actor) { store.find(f.company, f.account, f.device) })
        assertEquals(next.accessToken, found?.accessToken)
        assertEquals(next.generation, found?.generation)
        assertEquals(
            Result.Success(null),
            transactions.run(f.actor.copy(accountId = UUID.randomUUID())) {
                store.find(f.company, f.account, f.device)
            },
        )
        assertTrue(found.toString().contains("redacted"))
        assertFalse(found.toString().contains(next.accessToken))

        // Execute the production schema-sensitive recovery projection against the restored DB.
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("tool/recovery_set.py")) }
        val process =
            ProcessBuilder(
                    "python3",
                    "-c",
                    "import sys; sys.path.insert(0, 'tool'); import recovery_set; print(recovery_set.envelope_query(106))",
                )
                .directory(root.toFile())
                .start()
        val query =
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                assertEquals(0, process.exitValue())
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                }
                process.inputStream.close()
                process.errorStream.close()
                process.outputStream.close()
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
        val target = collaborationProviderPurpose(f.company, f.account, f.device, 1)
        val row =
            projection.stdout.lineSequence().map { it.split('\t') }.single { it.first() == target }
        assertEquals(next.accessToken, decryptIdentityEnvelope(currentKeys, row[0], row[1]))
        val all =
            projection.stdout
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { it.split('\t') }
                .toList()
        assertEquals(2, all.size)
        assertEquals(
            setOf(next.accessToken, "fictional-retained-old-key-token"),
            all.map { decryptIdentityEnvelope(currentKeys, it[0], it[1]) }.toSet(),
        )
    }

    @Test
    fun competingRotationsCommitOneCredentialGeneration() {
        val f = Fixture()
        assertEquals(Result.Success(Unit), f.transaction { f.current.create(f.session()) })
        val executor = Executors.newFixedThreadPool(2)
        val start = CyclicBarrier(2)
        try {
            val attempts =
                listOf("fictional-left", "fictional-right").map { token ->
                    executor.submit<Result<Unit>> {
                        start.await(5, TimeUnit.SECONDS)
                        f.transaction {
                            f.current.rotate(f.session(generation = 1, token = token), 0)
                        }
                    }
                }
            val results = attempts.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(
                listOf("collaboration_provider_generation_changed"),
                results.filterIsInstance<Result.Failed>().map { it.failure.code },
            )
            val session = success(f.transaction { f.current.find(f.company, f.account, f.device) })
            assertEquals(1L, session?.generation)
            assertTrue(session?.accessToken in setOf("fictional-left", "fictional-right"))
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun movingCiphertextToAnotherDeviceOrGenerationCannotDecrypt() {
        val first = Fixture()
        val second = Fixture()
        assertEquals(
            Result.Success(Unit),
            first.transaction { first.current.create(first.session()) },
        )
        assertEquals(
            Result.Success(Unit),
            first.transaction { first.current.rotate(first.session(generation = 1), 0) },
        )
        assertEquals(
            Result.Success(Unit),
            second.transaction { second.current.create(second.session()) },
        )
        sql.update(
            "update collaboration_provider_sessions set token_encrypted=?,generation=1,updated_at=now() where device_id=?",
            first.ciphertext(),
            second.device,
        )
        assertEquals(
            "identity_credential_failed",
            failure(
                second.transaction {
                    second.current.find(second.company, second.account, second.device)
                }
            ),
        )
        sql.update(
            "update collaboration_provider_sessions set generation=2,updated_at=updated_at+interval '1 second' where device_id=?",
            first.device,
        )
        assertEquals(
            "identity_credential_failed",
            failure(
                first.transaction { first.current.find(first.company, first.account, first.device) }
            ),
        )
        val wrongIdentity = Fixture()
        val values = wrongIdentity.session()
        assertInstanceOf(
            Result.Failed::class.java,
            wrongIdentity.transaction {
                wrongIdentity.current.create(
                    CollaborationProviderSession(
                        values.companyId,
                        values.accountId,
                        values.deviceId,
                        first.session().matrixUserId,
                        values.matrixDeviceId,
                        0,
                        values.accessToken,
                        values.createdAt,
                        values.updatedAt,
                    )
                )
            },
        )
    }

    @Test
    fun retirementErasesOnlyRevokedCredentialsAndPreventsRecreationOrStaleCleanup() {
        val f = Fixture()
        assertEquals(Result.Success(Unit), f.transaction { f.current.create(f.session()) })
        assertInstanceOf(
            Result.Failed::class.java,
            f.transaction { f.current.retire(f.company, f.account, f.device, 0, Instant.now()) },
        )
        f.revoke()
        assertEquals(
            Result.Success(Unit),
            f.transaction { f.current.retire(f.company, f.account, f.device, 0, Instant.now()) },
        )
        assertEquals(
            Result.Success(null),
            f.transaction { f.current.find(f.company, f.account, f.device) },
        )
        assertEquals(
            1L,
            sql.queryForObject(
                "select generation from collaboration_provider_sessions where device_id=?",
                Long::class.java,
                f.device,
            ),
        )
        assertNull(
            sql.queryForObject(
                "select token_encrypted from collaboration_provider_sessions where device_id=?",
                String::class.java,
                f.device,
            )
        )
        assertEquals(
            "collaboration_provider_generation_changed",
            failure(
                f.transaction { f.current.retire(f.company, f.account, f.device, 0, Instant.now()) }
            ),
        )
        assertInstanceOf(Result.Failed::class.java, f.transaction { f.current.create(f.session()) })
        assertEquals(
            "collaboration_provider_generation_changed",
            failure(f.transaction { f.current.rotate(f.session(generation = 2), 1) }),
        )
        assertThrows(Exception::class.java) {
            sql.update("delete from collaboration_provider_sessions where device_id=?", f.device)
        }
    }

    @Test
    fun aFailedBusinessTransactionRestoresThePreviousCredentialGeneration() {
        val f = Fixture()
        val original = f.session()
        assertEquals(Result.Success(Unit), f.transaction { f.current.create(original) })
        val encrypted = f.ciphertext()
        assertEquals(
            "fixture_rollback",
            failure(
                f.transaction {
                    f.current.rotate(f.session(generation = 1), 0).flatMap {
                        Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
                    }
                }
            ),
        )
        assertEquals(encrypted, f.ciphertext())
        assertEquals(
            0L,
            success(f.transaction { f.current.find(f.company, f.account, f.device) })?.generation,
        )
    }

    @Test
    fun revisionExhaustionStillAllowsIrreversibleDeviceAndCredentialRetirement() {
        val f = Fixture()
        assertEquals(Result.Success(Unit), f.transaction { f.current.create(f.session()) })
        // Seed the boundary under migration credentials instead of running a million rotations.
        TransactionTemplate(JdbcTransactionManager(admin)).executeWithoutResult {
            sql.execute(
                "alter table collaboration_devices disable trigger protect_collaboration_device"
            )
            sql.execute(
                "alter table collaboration_provider_sessions disable trigger protect_collaboration_provider_session"
            )
            sql.update("update collaboration_devices set version=999999 where id=?", f.device)
            sql.update(
                "update collaboration_provider_sessions set generation=999999 where device_id=?",
                f.device,
            )
            sql.execute(
                "alter table collaboration_devices enable trigger protect_collaboration_device"
            )
            sql.execute(
                "alter table collaboration_provider_sessions enable trigger protect_collaboration_provider_session"
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.transaction { f.current.rotate(f.session(generation = 1000000), 999999) },
        )
        f.revoke()
        assertEquals(
            1000000L,
            sql.queryForObject(
                "select version from collaboration_devices where id=?",
                Long::class.java,
                f.device,
            ),
        )
        assertEquals(
            Result.Success(Unit),
            f.transaction {
                f.current.retire(f.company, f.account, f.device, 999999, Instant.now())
            },
        )
        assertNull(
            sql.queryForObject(
                "select token_encrypted from collaboration_provider_sessions where device_id=?",
                String::class.java,
                f.device,
            )
        )
    }

    private class Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val device = UUID.randomUUID()
        val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val actor = Actor(account, company, emptySet(), at, UUID.randomUUID())
        private val source = runtime(databaseName)
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source))
        val old = repository(source, oldKeys)
        val current = repository(source, currentKeys)

        init {
            sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Provider fixture','UTC')",
                company,
                company.toString().take(12),
            )
            sql.update(
                "insert into accounts(id,email,display_name) values(?,?,'Provider fixture')",
                account,
                "$account@example.test",
            )
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional device','PROVISIONING',0,now(),now())",
                company,
                device,
                account,
            )
        }

        fun session(generation: Long = 0, token: String = "fictional-provider-token") =
            CollaborationProviderSession(
                company,
                account,
                device,
                "@hris_${company.toString().replace("-", "")}_${account.toString().replace("-", "")}:proof.invalid",
                "HRIS_${device.toString().replace("-", "")}",
                generation,
                token,
                at,
                at.plusSeconds(generation),
            )

        fun <T> transaction(action: () -> Result<T>): Result<T> = transactions.run(actor, action)

        fun ciphertext(): String =
            requireNotNull(
                sql.queryForObject(
                    "select token_encrypted from collaboration_provider_sessions where device_id=?",
                    String::class.java,
                    device,
                )
            )

        fun revoke() {
            sql.update(
                "update collaboration_devices set state='REVOKED',version=version+1,updated_at=now(),revoked_at=now() where id=?",
                device,
            )
        }
    }

    private fun command(vararg arguments: String) {
        val result = postgres.execInContainer(*arguments)
        assertEquals(0, result.exitCode, result.stderr)
    }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }

    private fun failure(value: Result<*>): String {
        assertInstanceOf(Result.Failed::class.java, value)
        return (value as Result.Failed).failure.code
    }
}

private fun runtime(database: String): DriverManagerDataSource =
    DriverManagerDataSource(
        CollaborationProviderCredentialTest.postgres.jdbcUrl.substringBeforeLast('/') +
            "/" +
            database,
        "provider_fixture",
        "provider-fixture-only",
    )

private fun repository(source: DriverManagerDataSource, keys: IdentityKeyring) =
    EncryptedCollaborationProviderSessionRepository(
        PostgresCollaborationProviderSessionDataSource(
            DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
        ),
        JceIdentityTokenDataSource(keys),
    )
