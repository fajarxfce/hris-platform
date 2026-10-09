package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.RequestJobCancellation
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired

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

    protected fun cleanupCollector() =
        documentCleanupWorker(postgres.jdbcUrl, database(), storageProbe, json)

    protected fun due(revision: UUID) {
        database()
            .update(
                "update object_cleanup_queue set eligible_at=clock_timestamp()-interval '1 second',version=version+1 where resource_id=?",
                revision,
            )
    }
}
