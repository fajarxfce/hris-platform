package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationPushBindingRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationPushBindingsRecord
import java.nio.file.Path
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.*
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationPushBindingStorageTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
        lateinit var owner: JdbcTemplate
        private val oldKey = Base64.getEncoder().encodeToString(ByteArray(32) { 18 })
        private val newKey = Base64.getEncoder().encodeToString(ByteArray(32) { 43 })
        private val oldKeys = parseIdentityKeyring("old", "old:$oldKey")
        private val currentKeys = parseIdentityKeyring("current", "old:$oldKey,current:$newKey")

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            owner = JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("114").load().migrate()
            val account = UUID.randomUUID()
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Pre-upgrade push fixture')",
                account,
                "$account@example.test",
            )
            val before =
                owner.queryForList(
                    "select row_to_json(a)::text as snapshot from accounts a order by id"
                )
            Flyway.configure().dataSource(source).target("115").load().migrate()
            assertEquals(
                before,
                owner.queryForList(
                    "select row_to_json(a)::text as snapshot from accounts a order by id"
                ),
            )
            owner.execute("create role chat_push_fixture login password 'chat-push-fixture-only'")
            owner.execute("grant usage on schema public to chat_push_fixture")
            owner.execute(
                "grant select,insert,update,delete on all tables in schema public to chat_push_fixture"
            )
        }
    }

    @Test
    fun encryptedIdentityHasOwnedScopeOnePublicationAndTransactionLocalLookup() {
        val f = Fixture()
        val binding = f.binding()
        assertEquals(Result.Success(Unit), f.run { f.repo.reserve(binding) })
        val key = f.key(binding)
        assertTrue(key.value.matches(Regex("hriscp1_[a-f0-9]{64}")))
        val ciphertext =
            owner.queryForObject(
                "select key_encrypted from collaboration_push_bindings where company_id=?",
                String::class.java,
                f.company,
            )!!
        assertFalse(ciphertext.contains(key.value))
        assertEquals(
            "Success(value=CollaborationPushKey(<redacted>))",
            Result.Success(key).toString(),
        )
        assertEquals(Result.Success(binding), f.run { f.repo.find(f.company, f.account, f.device) })
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = UUID.randomUUID()),
            )) {
            assertEquals(
                Result.Success(null),
                f.run(actor) { f.repo.find(f.company, f.account, f.device) },
            )
            assertEquals(Result.Success(null), f.run(actor) { f.repo.key(binding) })
            assertEquals(
                Result.Success(false),
                f.run(actor) { f.repo.publish(binding, f.now.plusSeconds(1)) },
            )
        }
        val lookup = f.actor.copy(accountId = UUID(0, 0), companyId = null)
        assertEquals(Result.Success(binding), f.run(lookup) { f.repo.findKey(key) })
        assertEquals(
            Result.Success(null),
            f.run(lookup) { f.repo.find(f.company, f.account, f.device) },
        )
        assertEquals(
            Result.Success(null),
            f.run(lookup) { f.repo.findKey(CollaborationPushKey("hriscp1_" + "f".repeat(64))) },
        )
        assertEquals(
            0,
            f.runtime.queryForObject(
                "select count(*) from collaboration_push_bindings",
                Int::class.java,
            ),
        )
        val at = f.now.plusSeconds(2)
        assertEquals(Result.Success(true), f.run { f.repo.publish(binding, at) })
        assertEquals(Result.Success(false), f.run { f.repo.publish(binding, at) })
        assertEquals(Result.Success(null), f.run { f.repo.key(binding) })
        val published = binding.copy(version = 1, publishedAt = at)
        assertEquals(key.value, f.key(published).value)
        assertInstanceOf(Result.Failed::class.java, f.run { f.repo.reserve(binding) })
        for (assignment in
            listOf(
                "key_hash=repeat('a',64)",
                "key_encrypted='replacement'",
                "native_session_id=gen_random_uuid()",
                "reserved_at=reserved_at+interval '1 second'",
                "version=0,published_at=null",
            )) {
            assertThrows(DataAccessException::class.java) {
                owner.update(
                    "update collaboration_push_bindings set $assignment where company_id=?",
                    f.company,
                )
            }
        }
        assertThrows(DataAccessException::class.java) {
            owner.update("delete from collaboration_push_bindings where company_id=?", f.company)
        }
    }

    @Test
    fun inactiveForeignAndMissingParentBindingsCannotBeCreatedOrPublished() {
        val f = Fixture()
        val binding = f.binding()
        for (invalid in
            listOf(
                binding.copy(accountId = UUID.randomUUID()),
                binding.copy(companyId = UUID.randomUUID()),
                binding.copy(deviceId = UUID.randomUUID()),
                binding.copy(nativeSessionId = UUID.randomUUID()),
                binding.copy(reservedAt = f.now.minusSeconds(121)),
                binding.copy(reservedAt = f.now.plusNanos(1)),
                binding.copy(version = 1, publishedAt = f.now),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run { f.repo.reserve(invalid) })
        }
        assertEquals(Result.Success(Unit), f.run { f.repo.reserve(binding) })
        val key = f.key(binding)
        owner.update(
            "update collaboration_devices set state='REVOKED',version=2,revoked_at=updated_at where company_id=? and id=?",
            f.company,
            f.device,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { f.repo.publish(binding, f.now.plusSeconds(1)) },
        )
        owner.update("delete from native_sessions where id=?", f.parent)
        assertEquals(Result.Success(binding), f.run { f.repo.find(f.company, f.account, f.device) })
        // Storage retains identity after purge. The callback policy must separately deny access.
        assertEquals(Result.Success(binding), f.run { f.repo.findKey(key) })
        val gone = Fixture()
        owner.update("delete from native_sessions where id=?", gone.parent)
        assertInstanceOf(Result.Failed::class.java, gone.run { gone.repo.reserve(gone.binding()) })
        val pending = Fixture(active = false)
        assertInstanceOf(
            Result.Failed::class.java,
            pending.run { pending.repo.reserve(pending.binding()) },
        )
    }

    @Test
    fun competingReservationsAndUncertainPublicationKeepOneEncryptedKey() {
        val f = Fixture()
        val binding = f.binding()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val waiting = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first =
                pool.submit<Result<Unit>> {
                    f.run {
                        f.repo.lock(f.company, f.device).flatMap {
                            entered.countDown()
                            assertTrue(release.await(3, TimeUnit.SECONDS))
                            f.repo.reserve(binding)
                        }
                    }
                }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val second =
                pool.submit<Result<CollaborationPushBinding?>> {
                    f.run {
                        waiting.countDown()
                        f.repo.lock(f.company, f.device).flatMap {
                            f.repo.find(f.company, f.account, f.device)
                        }
                    }
                }
            assertTrue(waiting.await(3, TimeUnit.SECONDS))
            assertFalse(second.isDone)
            release.countDown()
            assertEquals(Result.Success(Unit), first.get(5, TimeUnit.SECONDS))
            assertEquals(Result.Success(binding), second.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        val key = f.key(binding).value
        val denied = Result.Failed(Failure(FailureKind.FORBIDDEN, "fixture_late_denial"))
        assertEquals(
            denied,
            f.run {
                f.repo.publish(binding, f.now.plusSeconds(1)).flatMap<Boolean, Unit> { denied }
            },
        )
        try {
            assertThrows(InterruptedException::class.java) {
                f.run {
                    f.repo.publish(binding, f.now.plusSeconds(1)).flatMap<Boolean, Unit> {
                        throw InterruptedException()
                    }
                }
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(key, f.key(binding).value)
        assertEquals(Result.Success(true), f.run { f.repo.publish(binding, f.now.plusSeconds(2)) })
        val fresh = Fixture()
        assertEquals(
            denied,
            fresh.run { fresh.repo.reserve(fresh.binding()).flatMap<Unit, Unit> { denied } },
        )
        assertEquals(
            Result.Success(null),
            fresh.run { fresh.repo.find(fresh.company, fresh.account, fresh.device) },
        )
    }

    @Test
    fun ciphertextTransplantAndAlteredContextCannotProduceAProviderKey() {
        val f = Fixture()
        val other = Fixture()
        assertEquals(Result.Success(Unit), f.run { f.repo.reserve(f.binding()) })
        assertEquals(Result.Success(Unit), other.run { other.repo.reserve(other.binding()) })
        val foreign =
            other
                .run {
                    Result.Success(other.store.find(other.company, other.account, other.device)!!)
                }
                .let { (it as Result.Success).value }
        for (alter in
            listOf<(CollaborationPushBindingsRecord) -> Unit>(
                { it.keyEncrypted = foreign.keyEncrypted },
                { it.keyHash = "b".repeat(64) },
                { it.keyEncrypted = it.keyEncrypted.dropLast(4) },
            )) {
            val tampered =
                object : CollaborationPushBindingDataSource by f.store {
                    override fun find(
                        companyId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                    ): CollaborationPushBindingsRecord? =
                        f.store.find(companyId, accountId, deviceId)?.also(alter)
                }
            val repo =
                EncryptedCollaborationPushBindingRepository(
                    tampered,
                    JceIdentityTokenDataSource(currentKeys),
                )
            val result = f.run { repo.key(f.binding()) }
            assertInstanceOf(Result.Failed::class.java, result)
            assertFalse(result.toString().contains("hriscp1_"))
        }
        assertNotEquals(f.key(f.binding()).value, other.key(other.binding()).value)
    }

    @Test
    fun realRestoreAndRecoveryProjectionOpenOldAndCurrentKeyringEnvelopes() {
        val f = Fixture()
        val other = Fixture()
        val old =
            EncryptedCollaborationPushBindingRepository(
                f.store,
                JceIdentityTokenDataSource(oldKeys),
            )
        assertEquals(Result.Success(Unit), f.run { old.reserve(f.binding()) })
        assertEquals(Result.Success(Unit), other.run { other.repo.reserve(other.binding()) })
        val firstKey = f.key(f.binding()).value
        val secondKey = other.key(other.binding()).value
        val at = f.now.plusSeconds(1)
        assertEquals(Result.Success(true), f.run { f.repo.publish(f.binding(), at) })
        fun command(vararg args: String): String {
            val result = postgres.execInContainer(*args)
            assertEquals(0, result.exitCode, result.stderr)
            return result.stdout
        }
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/chat-push.dump",
        )
        command("createdb", "-U", postgres.username, "chat_push_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "chat_push_restored",
            "--exit-on-error",
            "/tmp/chat-push.dump",
        )
        val url =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/chat_push_restored"
        val source = DriverManagerDataSource(url, "chat_push_fixture", "chat-push-fixture-only")
        val runtime = JdbcTemplate(source)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), runtime)
        val repo =
            EncryptedCollaborationPushBindingRepository(
                PostgresCollaborationPushBindingDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
                ),
                JceIdentityTokenDataSource(currentKeys),
            )
        val published = f.binding().copy(version = 1, publishedAt = at)
        assertEquals(
            firstKey,
            (tx.run(f.actor) { repo.key(published) } as Result.Success).value!!.value,
        )
        assertEquals(
            secondKey,
            (tx.run(other.actor) { repo.key(other.binding()) } as Result.Success).value!!.value,
        )
        assertEquals(
            Result.Success(null),
            tx.run(f.actor.copy(accountId = UUID.randomUUID())) { repo.key(published) },
        )
        assertEquals(Result.Success(false), tx.run(f.actor) { repo.publish(f.binding(), at) })
        assertEquals(
            Result.Success(true),
            tx.run(other.actor) { repo.publish(other.binding(), other.now.plusSeconds(1)) },
        )
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { java.nio.file.Files.isRegularFile(it.resolve("tool/recovery_set.py")) }
        val process =
            ProcessBuilder(
                    "python3",
                    "-c",
                    "import sys; sys.path.insert(0, 'tool'); import recovery_set; print(recovery_set.envelope_query(115))",
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
            command(
                "psql",
                "-X",
                "-q",
                "-U",
                postgres.username,
                "-d",
                "chat_push_restored",
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                query,
            )
        val keys =
            projection
                .lineSequence()
                .filter { it.startsWith("hris:matrix-push:v1:") }
                .map { line ->
                    val (purpose, encrypted) = line.split('\t')
                    decryptIdentityEnvelope(currentKeys, purpose, encrypted)
                }
                .toSet()
        assertTrue(keys.containsAll(setOf(firstKey, secondKey)))
        assertEquals(
            0,
            runtime.queryForObject(
                "select count(*) from collaboration_push_bindings",
                Int::class.java,
            ),
        )
    }

    private class Fixture(active: Boolean = true) {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val device = UUID.randomUUID()
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val actor = Actor(account, company, emptySet(), now, UUID.randomUUID())
        private val source =
            DriverManagerDataSource(postgres.jdbcUrl, "chat_push_fixture", "chat-push-fixture-only")
        val runtime = JdbcTemplate(source)
        private val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), runtime)
        val store =
            PostgresCollaborationPushBindingDataSource(
                DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
            )
        val repo =
            EncryptedCollaborationPushBindingRepository(
                store,
                JceIdentityTokenDataSource(currentKeys),
            )

        init {
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional push company','UTC')",
                company,
                company.toString().replace("-", ""),
            )
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional push participant')",
                account,
                "$account@example.test",
            )
            val before = now.minusSeconds(120).atOffset(ZoneOffset.UTC)
            val hash = parent.toString().replace("-", "").repeat(2)
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,access_hash,authenticated_at,expires_at,access_expires_at,created_at,rotated_at) values(?,?,?,?,?,?,?,?,?)",
                parent,
                account,
                hash,
                hash,
                before,
                now.plusSeconds(86400).atOffset(ZoneOffset.UTC),
                now.plusSeconds(900).atOffset(ZoneOffset.UTC),
                before,
                before,
            )
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional push device','PROVISIONING',0,?,?)",
                company,
                device,
                account,
                before,
                before,
            )
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?)",
                company,
                device,
                account,
                parent,
                before,
            )
            if (active)
                owner.update(
                    "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                    company,
                    device,
                )
        }

        fun binding() = CollaborationPushBinding(company, account, device, parent, 0, now, null)

        fun key(binding: CollaborationPushBinding) =
            (run { repo.key(binding) } as Result.Success).value!!

        fun <T> run(actor: Actor = this.actor, work: () -> Result<T>): Result<T> =
            transactions.run(actor, work)
    }
}
