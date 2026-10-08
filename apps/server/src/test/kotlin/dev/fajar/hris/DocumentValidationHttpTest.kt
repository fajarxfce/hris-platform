package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class DocumentValidationHttpTest : DocumentValidationApiFixture() {
    @Test
    fun completeInspectionPublishesOnlyReadyContentAndRetainsImmutableEvidence() {
        val f = fixture()
        val document = UUID.randomUUID()
        val id = filled(f, document = document)
        val key = UUID.randomUUID()
        val submitted = validate(f, id, 1, key)
        assertEquals(200, submitted.statusCode(), submitted.body())
        assertEquals(submitted.body(), validate(f, id, 1, key).body())
        val lease = claimDocuments().single()
        assertEquals(Result.Success(JobStep(1, true)), run(f, lease))
        assertEquals("READY", revision(f, id).get("status").asString())
        assertEquals("SUCCEEDED", jobStatus(lease))
        assertEquals(0, garbage(id))
        assertEquals(1, scan.open.get())
        assertEquals(1, scan.closed.get())
        assertEquals(submitted.body(), validate(f, id, 1, key).body())
        val head = json.readTree(get(f.browser, "${f.path}/$document").body())
        assertEquals(id.toString(), head.get("currentRevisionId").asString())
        assertEquals(2, head.get("version").asInt())
        assertFalse(revision(f, id).toString().contains("object_key"))
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update document_revisions set scan_clean=false,version=version+1 where id=?",
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from document_validation_attempts where revision_id=?", id)
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from document_revisions where id=?", id)
        }
        assertEquals(409, cancel(f, id, 3).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='documents.validation_completed'",
                    Int::class.java,
                    id,
                ),
        )
        assertTrue(run(f, lease) is Result.Failed)
        val next = filled(f, document = document, version = 2)
        scan.clean = false
        assertEquals(Result.Success(JobStep(1, true)), run(f, beginValidation(f, next)))
        assertEquals(
            id.toString(),
            json
                .readTree(get(f.browser, "${f.path}/$document").body())
                .get("currentRevisionId")
                .asString(),
        )
    }

    @Test
    fun rejectedVerdictsTypesAndWholeFileDigestsNeverPublish() {
        for (case in listOf("unsafe", "type", "digest")) {
            val f = fixture()
            val id = UUID.randomUUID()
            val bytes = if (case == "type") "plain text fixture".toByteArray() else pdf
            val input =
                uploadInput(f, bytes, revision = id) +
                    (if (case == "digest") mapOf("sha256" to "0".repeat(64)) else emptyMap())
            assertEquals(200, start(f, input).statusCode())
            assertEquals(200, upload(f, id, bytes).statusCode())
            scan.clean = case != "unsafe"
            val lease = beginValidation(f, id)
            assertEquals(Result.Success(JobStep(1, true)), run(f, lease))
            assertEquals("SUCCEEDED", jobStatus(lease))
            val info = revision(f, id)
            assertEquals("REJECTED", info.get("status").asString())
            val code =
                when (case) {
                    "unsafe" -> "document_unsafe"
                    "type" -> "document_type_mismatch"
                    else -> "document_integrity_mismatch"
                }
            assertEquals(code, info.get("failureCode").asString())
            assertEquals(1, garbage(id))
            assertTrue(
                database()
                    .queryForObject(
                        "select eligible_at<clock_timestamp()+interval '6 minutes' from object_cleanup_queue where resource_id=?",
                        Boolean::class.java,
                        id,
                    )!!
            )
            assertEquals(409, validate(f, id).statusCode())
        }
    }

    @Test
    fun incompleteUploadsAndCompetingValidationRequestsCannotCreateDuplicateJobs() {
        val f = fixture()
        val id = begin(f, pdf)
        assertEquals(409, validate(f, id).statusCode())
        assertEquals(200, upload(f, id, pdf).statusCode())
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val submissions =
                (1..2).map {
                    pool.submit<Int> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        validate(f, id, 1).statusCode()
                    }
                }
            gate.countDown()
            assertEquals(
                listOf(200, 409),
                submissions.map { it.get(10, TimeUnit.SECONDS) }.sorted(),
            )
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_validation_attempts where revision_id=?",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(409, validate(f, id).statusCode())
        val other = fixture()
        assertEquals(404, validate(other, id, 2).statusCode())
    }

    @Test
    fun cancellationRevocationAndExpiryDuringInspectionRejectLateResults() {
        for (mode in listOf("cancel", "revoke", "expire")) {
            val f = fixture()
            val id = filled(f)
            val lease = beginValidation(f, id)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            scan.beforeFinish = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<JobStep>> { run(f, lease) }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    when (mode) {
                        "cancel" -> assertEquals(200, cancel(f, id, 2).statusCode())
                        "revoke" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='documents.manage'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        else -> clock.set(Instant.now().plusSeconds(86401))
                    }
                } finally {
                    release.countDown()
                }
                val failed = pending.get(10, TimeUnit.SECONDS)
                assertTrue(failed is Result.Failed, failed.toString())
                assertEquals(
                    Result.Success(Unit),
                    abort.execute(lease, (failed as Result.Failed).failure),
                )
            }
            scan.beforeFinish = null
            assertEquals(if (mode == "cancel") "CANCELLED" else "FAILED", jobStatus(lease))
            assertNotEquals(
                "READY",
                database()
                    .queryForObject(
                        "select status from document_revisions where id=?",
                        String::class.java,
                        id,
                    ),
            )
            assertEquals(1, garbage(id))
            assertEquals(scan.open.get(), scan.closed.get())
            clock.set(Instant.now())
        }
    }

    @Test
    fun aReclaimedLeaseFencesTheOldInspectorEvenWhenTheVersionDidNotChange() {
        val f = fixture()
        val id = filled(f)
        val old = beginValidation(f, id)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        scan.beforeFinish = {
            if (first.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { run(f, old) }
            val replacement: JobLease
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                        old.job.request.id,
                    )
                replacement = claimDocuments().single()
                assertNotEquals(old.token, replacement.token)
                assertEquals(Result.Success(JobStep(1, true)), run(f, replacement))
            } finally {
                release.countDown()
            }
            assertTrue(pending.get(10, TimeUnit.SECONDS) is Result.Failed)
        }
        assertEquals("READY", revision(f, id).get("status").asString())
        assertEquals(0, garbage(id))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='documents.validation_completed'",
                    Int::class.java,
                    id,
                ),
        )
    }

    @Test
    fun failedAuditRollsBackRetentionPublicationAndCheckpointTogether() {
        val f = fixture()
        val id = filled(f)
        val lease = beginValidation(f, id)
        database()
            .execute(
                """create function fail_validation_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$id'::uuid and new.action='documents.validation_completed' then raise exception 'Fixture' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger validation_audit_probe before insert on audit_entries for each row execute function fail_validation_audit()"
            )
        try {
            assertTrue(run(f, lease) is Result.Failed)
            assertEquals(1, garbage(id))
            assertEquals("VALIDATING", revision(f, id).get("status").asString())
            assertEquals("RUNNING", jobStatus(lease))
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select completed_items from background_jobs where id=?",
                        Int::class.java,
                        lease.job.request.id,
                    ),
            )
            assertNull(
                database()
                    .queryForObject(
                        "select current_revision_id from documents where company_id=?",
                        UUID::class.java,
                        f.company,
                    )
            )
        } finally {
            database().execute("drop trigger validation_audit_probe on audit_entries")
            database().execute("drop function fail_validation_audit()")
        }
        assertEquals(Result.Success(JobStep(1, true)), run(f, lease))
        assertEquals(0, garbage(id))
    }

    @Test
    fun partialCleanupRetentionRollsBackWhenAnyAcceptedObjectHasExpired() {
        val f = fixture()
        val bytes = ByteArray(1048577) { 32 }
        pdf.copyInto(bytes)
        val id = filled(f, bytes)
        val lease = beginValidation(f, id)
        database()
            .update(
                "delete from object_cleanup_queue where id=(select id from object_cleanup_queue where resource_id=? order by id limit 1)",
                id,
            )
        val result = run(f, lease)
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "document_content_expired")),
            result,
        )
        assertEquals(1, garbage(id))
        assertEquals("VALIDATING", revision(f, id).get("status").asString())
        assertEquals("RUNNING", jobStatus(lease))
    }

    @Test
    fun inspectionFailuresHaveBoundedExplicitRecoveryAndKeepEveryAttempt() {
        val f = fixture()
        val id = filled(f)
        repeat(8) {
            val lease = beginValidation(f, id)
            assertEquals(
                Result.Success(Unit),
                abort.execute(
                    lease,
                    Failure(FailureKind.UNAVAILABLE, "document_inspection_unavailable"),
                ),
            )
            assertEquals("VALIDATION_FAILED", revision(f, id).get("status").asString())
            assertEquals("FAILED", jobStatus(lease))
        }
        val rejected = validate(f, id)
        assertEquals(409, rejected.statusCode(), rejected.body())
        assertEquals(
            "document_validation_attempts_exhausted",
            json.readTree(rejected.body()).get("code").asString(),
        )
        assertEquals(
            8,
            database()
                .queryForObject(
                    "select count(*) from document_validation_attempts where revision_id=?",
                    Int::class.java,
                    id,
                ),
        )
        val history = get(f.browser, "${f.path}/revisions/$id/validation-attempts")
        assertEquals(200, history.statusCode(), history.body())
        assertEquals(8, json.readTree(history.body()).size())
        val other = fixture()
        assertEquals(
            404,
            get(other.browser, "${other.path}/revisions/$id/validation-attempts").statusCode(),
        )
        assertEquals(1, garbage(id))
    }

    @Test
    fun crashedJobExhaustionIsProjectedAndAnExplicitRetryCanPublish() {
        val f = fixture()
        val id = filled(f)
        val old = beginValidation(f, id)
        database()
            .update(
                "update background_jobs set attempts=8,lease_until=clock_timestamp()-interval '1 second' where id=?",
                old.job.request.id,
            )
        assertTrue(claimDocuments().isEmpty())
        assertEquals("VALIDATION_FAILED", revision(f, id).get("status").asString())
        assertEquals("job_attempts_exhausted", revision(f, id).get("failureCode").asString())
        val next = beginValidation(f, id)
        assertNotEquals(old.job.request.id, next.job.request.id)
        assertEquals(
            Result.Success(Unit),
            abort.execute(old, Failure(FailureKind.CONFLICT, "late_abort")),
        )
        assertEquals(Result.Success(JobStep(1, true)), run(f, next))
        assertEquals("FAILED", jobStatus(old))
    }

    @Test
    fun readyRevisionsContinueConsumingQuotaAfterTemporaryKeysAreRetained() {
        val f = fixture()
        val id = filled(f)
        assertEquals(Result.Success(JobStep(1, true)), run(f, beginValidation(f, id)))
        database()
            .update(
                """insert into object_cleanup_queue(id,company_id,resource_id,object_key,object_bytes,created_by,eligible_at)
            select gen_random_uuid(),?,gen_random_uuid(),? || '/fixture/' || n::text,5242880,?,clock_timestamp()+interval '1 day' from generate_series(1,2047) n""",
                f.company,
                f.company.toString(),
                f.actor.accountId,
            )
        database()
            .update(
                "insert into object_cleanup_queue(id,company_id,resource_id,object_key,object_bytes,created_by,eligible_at) values(gen_random_uuid(),?,gen_random_uuid(),?, ?,?,clock_timestamp()+interval '1 day')",
                f.company,
                "${f.company}/quota/remainder",
                5242880 - pdf.size,
                f.actor.accountId,
            )
        assertEquals(409, start(f, uploadInput(f, pdf)).statusCode())
    }
}
