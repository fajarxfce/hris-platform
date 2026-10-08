package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.RequestJobCancellation
import dev.fajar.hris.storage.data.datasources.PostgresObjectCleanupDataSource
import dev.fajar.hris.storage.data.repositories.*
import dev.fajar.hris.storage.domain.usecases.CollectObjectGarbage
import java.time.*
import java.util.UUID
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

abstract class DocumentInventoryApiFixture : DocumentValidationApiFixture() {
    @Autowired protected lateinit var inventoryAdvance: AdvanceDocumentInventory
    @Autowired protected lateinit var inventoryAbort: AbortDocumentInventory
    @Autowired protected lateinit var inventoryStart: StartDocumentInventory
    @Autowired protected lateinit var cancelJob: RequestJobCancellation

    @AfterEach
    fun closeInventoryJobs() {
        storageProbe.beforeList = null
        database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where kind='DOCUMENT_INVENTORY' and status in ('QUEUED','RUNNING')"
            )
    }

    protected fun startInventory(
        f: Fixture,
        id: UUID = UUID.randomUUID(),
        operation: UUID = UUID.randomUUID(),
        reason: String = "Reconcile private storage",
    ) =
        command(
            f.browser,
            "${f.path}/inventory",
            json.writeValueAsString(mapOf("runId" to id, "reason" to reason)),
            f.csrf,
            operation,
        )

    protected fun beginInventory(f: Fixture, id: UUID = UUID.randomUUID()): JobLease {
        val response = startInventory(f, id)
        assertEquals(200, response.statusCode(), response.body())
        return claimDocuments(JobKind.DOCUMENT_INVENTORY).single {
            it.job.request.values["runId"] == id.toString()
        }
    }

    protected fun inventory(f: Fixture, id: UUID): tools.jackson.databind.JsonNode {
        val response = get(f.browser, "${f.path}/inventory/$id")
        assertEquals(200, response.statusCode(), response.body())
        assertFalse(response.body().contains("lastKey"))
        assertFalse(response.body().contains("${f.company}/"))
        return json.readTree(response.body())
    }

    protected fun inventoryId(lease: JobLease) =
        UUID.fromString(lease.job.request.values.getValue("runId"))

    protected fun runInventory(f: Fixture, lease: JobLease) =
        inventoryAdvance.execute(
            f.actor.copy(credentialVersion = lease.job.request.credentialVersion),
            lease,
        )

    protected fun resumeInventory(
        f: Fixture,
        id: UUID,
        version: Long,
        operation: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/inventory/$id/resume",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Resume interrupted reconciliation")
            ),
            f.csrf,
            operation,
        )

    protected fun makeOld(key: String) {
        storageProbe.objects.computeIfPresent(key) { _, value ->
            value.copy(modifiedAt = clock.instant().minusSeconds(172800))
        }
    }

    protected fun orphan(f: Fixture): String {
        val revision = filled(f)
        assertEquals(200, cancel(f, revision, 1).statusCode())
        database().update("delete from object_cleanup_queue where resource_id=?", revision)
        val key = storageProbe.objects.keys.single { it.startsWith("${f.company}/$revision/") }
        makeOld(key)
        return key
    }

    protected fun inventoryPages(id: UUID) =
        database()
            .queryForObject(
                "select count(*) from document_inventory_pages where run_id=?",
                Int::class.java,
                id,
            )

    protected fun cleanupCollector(): CollectObjectGarbage {
        database()
            .execute(
                """do ${'$'}${'$'} begin if not exists(select 1 from pg_roles where rolname='inventory_cleanup_test') then create role inventory_cleanup_test login password 'inventory-fixture-only' nosuperuser nobypassrls;end if;end ${'$'}${'$'}"""
            )
        database().execute("grant hris_worker_capability to inventory_cleanup_test")
        database().execute("grant usage on schema public to inventory_cleanup_test")
        database()
            .execute(
                "grant select,insert,update,delete on all tables in schema public to inventory_cleanup_test"
            )
        val source =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "inventory_cleanup_test",
                "inventory-fixture-only",
            )
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(source),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val queue = PostgresObjectCleanupRepository(PostgresObjectCleanupDataSource(sql))
        val journal = PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json))
        return CollectObjectGarbage(
            queue,
            PrivateObjectStorageRepository(storageProbe),
            journal,
            PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source)),
            Clock.systemUTC(),
        )
    }

    protected fun due(revision: UUID) {
        database()
            .update(
                "update object_cleanup_queue set eligible_at=clock_timestamp()-interval '1 second',version=version+1 where resource_id=?",
                revision,
            )
    }
}
