package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class DocumentInventoryHttpTest : DocumentInventoryApiFixture() {
    @Test
    fun inventoryRecoversAPutThatArrivedAfterCleanupAcknowledgement() {
        val f = fixture()
        val revision = begin(f, pdf)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val objectKey = AtomicReference<String>()
        storageProbe.beforeWrite = { key ->
            objectKey.set(key)
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val collect = cleanupCollector()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> { upload(f, revision, pdf) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(200, cancel(f, revision, 0).statusCode())
                due(revision)
                assertEquals(Result.Success(1), collect.execute(UUID.randomUUID()))
                assertEquals(0, garbage(revision))
                assertFalse(storageProbe.objects.containsKey(objectKey.get()))
            } finally {
                release.countDown()
            }
            val result = pending.get(10, TimeUnit.SECONDS)
            assertEquals(409, result.statusCode(), result.body())
        }
        storageProbe.beforeWrite = null
        assertTrue(storageProbe.objects.containsKey(objectKey.get()))
        makeOld(objectKey.get())
        val lease = beginInventory(f)
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, lease))
        val result = inventory(f, inventoryId(lease))
        assertEquals("COMPLETED", result.get("status").asString())
        assertEquals(1, result.get("scheduled").asInt())
        assertEquals(1, garbage(revision))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_inventory_recoveries where run_id=?",
                    Int::class.java,
                    inventoryId(lease),
                ),
        )
        assertTrue(storageProbe.objects.containsKey(objectKey.get()))
        due(revision)
        assertEquals(Result.Success(1), collect.execute(UUID.randomUUID()))
        assertFalse(storageProbe.objects.containsKey(objectKey.get()))
        val job = get(f.browser, "/api/v1/companies/${f.company}/jobs/${lease.job.request.id}")
        assertEquals(200, job.statusCode(), job.body())
        val progress = json.readTree(job.body())
        assertEquals("UPPER_BOUND", progress.get("progressMode").asString())
        assertEquals(1, progress.get("completedItems").asInt())
        assertEquals(10000, progress.get("totalItems").asInt())
    }

    @Test
    fun acceptedHistoryActiveRecentUnknownAndAnomalousObjectsArePreserved() {
        val f = fixture()
        val document = UUID.randomUUID()
        val old = filled(f, document = document)
        assertEquals(Result.Success(JobStep(1, true)), run(f, beginValidation(f, old)))
        val latest = filled(f, document = document, version = 2)
        assertEquals(Result.Success(JobStep(1, true)), run(f, beginValidation(f, latest)))
        val active = filled(f)
        val recent = orphan(f)
        val mismatch = orphan(f)
        val queued = filled(f)
        assertEquals(200, cancel(f, queued, 1).statusCode())
        database()
            .update(
                "update object_cleanup_queue set status='FAILED',failure_code='fixture_failure',version=version+1 where resource_id=?",
                queued,
            )
        val disposable = orphan(f)
        storageProbe.objects.keys.forEach(::makeOld)
        storageProbe.objects.computeIfPresent(recent) { _, v ->
            v.copy(modifiedAt = clock.instant())
        }
        storageProbe.objects.computeIfPresent(mismatch) { _, v ->
            v.copy(bytes = v.bytes + byteArrayOf(1))
        }
        storageProbe.objects["${f.company}/"] = DocumentStorageProbe.Value(byteArrayOf(), "folder")
        storageProbe.objects["${f.company}/観光\u0000"] =
            DocumentStorageProbe.Value(byteArrayOf(1), "unknown")
        val lease = beginInventory(f)
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, lease))
        val result = inventory(f, inventoryId(lease))
        assertEquals(4, result.get("retained").asInt())
        assertEquals(2, result.get("unknown").asInt())
        assertEquals(1, result.get("anomalous").asInt())
        assertEquals(1, result.get("queued").asInt())
        assertEquals(1, result.get("scheduled").asInt())
        assertEquals(9, result.get("scanned").asInt())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where object_key=?",
                    Int::class.java,
                    disposable,
                ),
        )
        assertEquals(1, garbage(active))
        assertEquals(0, garbage(old))
        assertEquals(0, garbage(latest))
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from object_cleanup_queue where resource_id=?",
                    String::class.java,
                    queued,
                ),
        )
        assertEquals(9, storageProbe.objects.size)
    }

    @Test
    fun failedScansResumeTheirFiniteCursorAndKeepPageAndJobEvidence() {
        val f = fixture()
        repeat(201) { index ->
            storageProbe.objects["${f.company}/unknown-${index.toString().padStart(4,'0')}"] =
                DocumentStorageProbe.Value(byteArrayOf(1), "unknown")
        }
        val lease = beginInventory(f)
        val id = inventoryId(lease)
        assertEquals(Result.Success(JobStep(1, false)), runInventory(f, lease))
        storageProbe.failList = true
        val failed = runInventory(f, lease)
        assertTrue(failed is Result.Failed, failed.toString())
        assertEquals(
            Result.Success(Unit),
            inventoryAbort.execute(lease, (failed as Result.Failed).failure),
        )
        assertEquals("FAILED", inventory(f, id).get("status").asString())
        storageProbe.failList = false
        val operation = UUID.randomUUID()
        val resumed = resumeInventory(f, id, 1, operation)
        assertEquals(200, resumed.statusCode(), resumed.body())
        assertEquals(resumed.body(), resumeInventory(f, id, 1, operation).body())
        val next = claimDocuments(JobKind.DOCUMENT_INVENTORY).single()
        assertNotEquals(lease.job.request.id, next.job.request.id)
        assertTrue(runInventory(f, lease) is Result.Failed)
        assertEquals(Result.Success(JobStep(1, false)), runInventory(f, next))
        assertEquals(Result.Success(JobStep(2, true)), runInventory(f, next))
        val result = inventory(f, id)
        assertEquals(201, result.get("unknown").asInt())
        assertEquals(3, result.get("pages").asInt())
        assertEquals(2, result.get("attempts").asInt())
        assertEquals("COMPLETED", result.get("status").asString())
        assertEquals(3, inventoryPages(id))
        assertEquals(storageProbe.cursors[1], storageProbe.cursors[2])
        val history = get(f.browser, "${f.path}/inventory/$id/attempts")
        assertEquals(200, history.statusCode())
        assertEquals(
            listOf("FAILED", "SUCCEEDED"),
            json
                .readTree(history.body())
                .iterator()
                .asSequence()
                .map { it.get("status").asString() }
                .toList(),
        )
        assertEquals(
            2,
            json.readTree(get(f.browser, "${f.path}/inventory/$id/pages?after=1").body()).size(),
        )
        assertThrows(DataAccessException::class.java) {
            database().update("delete from document_inventory_pages where run_id=?", id)
        }
        assertThrows(DataAccessException::class.java) {
            database().update("update document_inventory_runs set version=version+1 where id=?", id)
        }
    }

    @Test
    fun cleanupRecoveryStopsAfterThreeAttemptsAndCannotResetFailedQueueEntries() {
        val f = fixture()
        val key = orphan(f)
        repeat(3) {
            val lease = beginInventory(f)
            assertEquals(Result.Success(JobStep(1, true)), runInventory(f, lease))
            assertEquals(1, inventory(f, inventoryId(lease)).get("scheduled").asInt())
            // Model a later physical reappearance after a completed cleanup registration.
            database().update("delete from object_cleanup_queue where object_key=?", key)
        }
        val last = beginInventory(f)
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, last))
        assertEquals(1, inventory(f, inventoryId(last)).get("recoveryExhausted").asInt())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where object_key=?",
                    Int::class.java,
                    key,
                ),
        )
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select count(*) from document_inventory_recoveries where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun cancellationAndEightExplicitAttemptsPreserveTheOriginalCheckpoint() {
        val f = fixture()
        val id = UUID.randomUUID()
        var lease = beginInventory(f, id)
        repeat(8) { attempt ->
            assertTrue(
                cancelJob.execute(f.actor, lease.job.request.id, lease.job.version)
                    is Result.Success
            )
            assertEquals(
                Result.Failed(Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
                runInventory(f, lease),
            )
            assertEquals(
                Result.Success(Unit),
                inventoryAbort.execute(
                    lease,
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
                ),
            )
            assertEquals("CANCELLED", inventory(f, id).get("status").asString())
            if (attempt < 7) {
                val resumed = resumeInventory(f, id, attempt.toLong())
                assertEquals(200, resumed.statusCode(), resumed.body())
                lease = claimDocuments(JobKind.DOCUMENT_INVENTORY).single()
            }
        }
        assertEquals(409, resumeInventory(f, id, 7).statusCode())
        assertEquals(0, storageProbe.listings.get())
        assertEquals(
            8,
            json.readTree(get(f.browser, "${f.path}/inventory/$id/attempts").body()).size(),
        )
        assertEquals(0, inventory(f, id).get("availableActions").size())
    }

    @Test
    fun auditFailureRollsBackCleanupEvidenceProgressAndCompletion() {
        val f = fixture()
        val key = orphan(f)
        val lease = beginInventory(f)
        val id = inventoryId(lease)
        database()
            .execute(
                """create function fail_inventory_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$id'::uuid and new.action='documents.inventory_page' then raise exception 'Fixture' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger inventory_audit_probe before insert on audit_entries for each row execute function fail_inventory_audit()"
            )
        try {
            assertTrue(runInventory(f, lease) is Result.Failed)
            assertEquals(0, inventoryPages(id))
            assertEquals(0, inventory(f, id).get("scanned").asInt())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where object_key=?",
                        Int::class.java,
                        key,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from document_inventory_recoveries where run_id=?",
                        Int::class.java,
                        id,
                    ),
            )
            assertEquals("RUNNING", jobStatus(lease))
        } finally {
            database().execute("drop trigger inventory_audit_probe on audit_entries")
            database().execute("drop function fail_inventory_audit()")
        }
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, lease))
        assertEquals(1, inventoryPages(id))
    }

    @Test
    fun overlappingPageReadsCommitOnlyOneCleanupAndCheckpoint() {
        val f = fixture()
        orphan(f)
        val lease = beginInventory(f)
        val barrier = CyclicBarrier(2)
        storageProbe.beforeList = {
            barrier.await(8, TimeUnit.SECONDS)
            Unit
        }
        Executors.newFixedThreadPool(2).use { pool ->
            val work = (1..2).map { pool.submit<Result<JobStep>> { runInventory(f, lease) } }
            val results = work.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
        }
        storageProbe.beforeList = null
        assertEquals(1, inventoryPages(inventoryId(lease)))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_inventory_recoveries where run_id=?",
                    Int::class.java,
                    inventoryId(lease),
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='documents.inventory_page'",
                    Int::class.java,
                    inventoryId(lease),
                ),
        )
    }
}
