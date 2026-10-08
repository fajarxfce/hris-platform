package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.storage.data.datasources.*
import dev.fajar.hris.storage.data.models.ObjectMetadataData
import dev.fajar.hris.storage.data.repositories.*
import dev.fajar.hris.storage.domain.entities.*
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import dev.fajar.hris.storage.domain.usecases.CollectObjectGarbage
import java.io.IOException
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

abstract class ObjectCleanupApiFixture : ApiIntegrationTest() {
    @Autowired protected lateinit var queue: ObjectCleanupRepository
    @Autowired protected lateinit var transactions: TransactionRunner

    protected data class Fixture(val browser: HttpClient, val csrf: String, val actor: Actor) {
        val company: UUID
            get() = requireNotNull(actor.companyId)

        val path: String
            get() = "/api/v1/companies/$company/storage-cleanup"
    }

    protected class StorageProbe : ObjectStorageDataSource {
        val deleted = java.util.concurrent.CopyOnWriteArrayList<String>()
        val calls = AtomicInteger()
        var fail = false

        override fun delete(key: String) {
            check(!TransactionSynchronizationManager.isActualTransactionActive())
            calls.incrementAndGet()
            if (fail) throw IOException("fixture-unavailable")
            deleted += key
        }

        override fun list(
            prefix: String,
            afterKey: String?,
            limit: Int,
        ): dev.fajar.hris.storage.data.models.ObjectInventoryPageData =
            throw UnsupportedOperationException("Listing is not part of this fixture")

        override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData =
            throw UnsupportedOperationException()

        override fun metadata(key: String): ObjectMetadataData =
            throw UnsupportedOperationException()

        override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray =
            throw UnsupportedOperationException()

        override fun close() {}
    }

    protected data class Worker(
        val queue: ObjectCleanupRepository,
        val transactions: TransactionRunner,
        val collect: CollectObjectGarbage,
        val jdbc: JdbcTemplate,
    )

    private val companies = mutableListOf<UUID>()

    protected fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        val result =
            command(
                browser,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "S${UUID.randomUUID().toString().take(8)}",
                        "name" to "Storage fixture",
                        "timezone" to "UTC",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, result.statusCode(), result.body())
        val company = UUID.fromString(json.readTree(result.body()).get("id").asString())
        companies += company
        val account =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        return Fixture(
            browser,
            csrf,
            Actor(
                account,
                company,
                PermissionCatalog.companyAdministrator,
                Instant.now(),
                UUID.randomUUID(),
            ),
        )
    }

    @AfterEach
    fun removeFixtureObjects() {
        companies.forEach {
            database().update("delete from object_cleanup_queue where company_id=?", it)
        }
    }

    protected fun schedule(f: Fixture, due: Boolean = true): ObjectCleanupRequest {
        val id = UUID.randomUUID()
        val resource = UUID.randomUUID()
        val request =
            ObjectCleanupRequest(
                id,
                f.company,
                resource,
                "${f.company}/$resource/$id",
                128,
                f.actor.accountId,
                Instant.now().plusSeconds(if (due) -1 else 3600),
            )
        assertEquals(Result.Success(Unit), transactions.run(f.actor) { queue.schedule(request) })
        return request
    }

    protected fun worker(probe: StorageProbe = StorageProbe()): Worker {
        database()
            .execute(
                """do ${'$'}${'$'} begin if not exists(select 1 from pg_roles where rolname='cleanup_worker_test') then create role cleanup_worker_test login password 'cleanup-fixture-only' nosuperuser nobypassrls;end if;end ${'$'}${'$'}"""
            )
        database().execute("grant hris_worker_capability to cleanup_worker_test")
        database().execute("grant usage on schema public to cleanup_worker_test")
        database()
            .execute(
                "grant select,insert,update,delete on all tables in schema public to cleanup_worker_test"
            )
        val connection =
            DriverManagerDataSource(postgres.jdbcUrl, "cleanup_worker_test", "cleanup-fixture-only")
        val manager = JdbcTransactionManager(connection)
        val jdbc = JdbcTemplate(connection)
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(connection),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val transactions = PostgresTransactionRunner(manager, jdbc)
        val queue = PostgresObjectCleanupRepository(PostgresObjectCleanupDataSource(sql))
        val journal = PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json))
        return Worker(
            queue,
            transactions,
            CollectObjectGarbage(
                queue,
                PrivateObjectStorageRepository(probe),
                journal,
                transactions,
                Clock.systemUTC(),
            ),
            jdbc,
        )
    }

    protected fun expire(id: UUID) {
        database()
            .update(
                "update object_cleanup_queue set lease_until=clock_timestamp()-interval '1 second',version=version+1 where id=?",
                id,
            )
    }

    protected fun due(id: UUID) {
        database()
            .update(
                "update object_cleanup_queue set eligible_at=clock_timestamp()-interval '1 second',version=version+1 where id=?",
                id,
            )
    }

    protected fun retry(f: Fixture, id: UUID, version: Long, key: UUID = UUID.randomUUID()) =
        command(
            f.browser,
            "${f.path}/$id/retry",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Storage access restored")
            ),
            f.csrf,
            key,
        )
}
