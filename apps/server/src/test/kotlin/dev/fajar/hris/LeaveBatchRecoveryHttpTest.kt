package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveBatchRecoveryHttpTest : LeaveBatchApiFixture() {
    @Test
    fun cancellationAndExplicitResumeRetainCompletedEmployeesAndStartANewAttempt() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val id = batchId(lease)
        assertEquals(Result.Success(JobStep(1, false)), stepBatch(f, lease))
        cancelBatch(f, lease)
        val stopped = batchView(f, id)
        assertEquals("STOPPED", stopped["batch"]["status"].asString())
        assertEquals("CANCELLED", stopped["job"]["status"].asString())
        assertEquals(1, stopped["counts"]["completed"].asInt())
        val retained = stopped["results"]["items"][0].toString()
        val key = UUID.randomUUID()
        val resumed = resumeBatch(f, id, 1, key)
        accountingBody(resumed)
        assertEquals(resumed.body(), resumeBatch(f, id, 1, key).body())
        val next = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        assertNotEquals(lease.job.request.id, next.job.request.id)
        assertEquals(2, next.job.request.totalItems)
        drainBatch(f, next)
        val done = batchView(f, id)
        assertEquals(2, done["attempts"].size())
        assertEquals(1, done["attempts"][1]["baseCompleted"].asInt())
        assertEquals(2, done["counts"]["applied"].asInt())
        assertEquals(retained, done["results"]["items"][0].toString())
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
        accountingCode(resumeBatch(f, id, 1), 409, "stale_version")
        failure(stepBatch(f, lease), "job_lease_lost")
        assertEquals(
            Result.Success(Unit),
            abortBatch.execute(lease, Failure(FailureKind.CONFLICT, "job_lease_lost")),
        )
    }

    @Test
    fun expiredLeasesCannotWriteAfterAnotherWorkerAcquiresTheJob() {
        val f = accountingFixture()
        val old = beginBatch(f)
        val id = batchId(old)
        assertTrue(stepBatch(f, old) is Result.Success)
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                old.job.request.id,
            )
        val current = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        assertNotEquals(old.token, current.token)
        failure(stepBatch(f, old), "job_lease_lost")
        assertEquals(
            Result.Success(Unit),
            abortBatch.execute(old, Failure(FailureKind.CONFLICT, "job_lease_lost")),
        )
        drainBatch(f, current)
        val done = batchView(f, id)
        assertEquals("SUCCEEDED", done["job"]["status"].asString())
        assertEquals(2, done["job"]["attempts"].asInt())
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
        assertEquals(1, done["attempts"].size())
    }

    @Test
    fun changedPoliciesStopTheFrozenBatchAndANewBatchSkipsExistingAwards() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val id = batchId(lease)
        assertTrue(stepBatch(f, lease) is Result.Success)
        accountingBody(accountingPolicy(f, version = 1, days = "2"))
        val rejected = stepBatch(f, lease)
        failure(rejected, "leave_batch_policy_changed")
        assertEquals(
            Result.Success(Unit),
            abortBatch.execute(lease, (rejected as Result.Failed).failure),
        )
        accountingCode(resumeBatch(f, id, 1), 409, "leave_batch_policy_changed")
        val replacement = UUID.randomUUID()
        accountingBody(createBatch(f, batchBody(f, replacement, version = 2)))
        val fresh = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == replacement }
        drainBatch(f, fresh)
        val done = batchView(f, replacement)
        assertEquals(1, done["counts"]["unchanged"].asInt())
        assertEquals(1, done["counts"]["applied"].asInt())
        assertEquals(
            6,
            database()
                .queryForObject(
                    "select sum(half_days)::integer from leave_accrual_postings where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun revocationBetweenStepsStopsWorkAndResumeNeedsRestoredOriginalAuthority() {
        val f = accountingFixture()
        val actor = accountingActor(f)
        val lease = beginBatch(f)
        val id = batchId(lease)
        assertTrue(stepBatch(f, lease, actor) is Result.Success)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.accrual.post'",
                f.company,
                actor.accountId,
            )
        val denied = stepBatch(f, lease, actor)
        failure(denied, "access_denied")
        assertEquals(
            Result.Success(Unit),
            abortBatch.execute(lease, (denied as Result.Failed).failure),
        )
        assertEquals(1, accountingRows(f, "leave_accrual_postings"))
        accountingCode(resumeBatch(f, id, 1), 403, "access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.accrual.post')",
                f.company,
                actor.accountId,
            )
        accountingBody(resumeBatch(f, id, 1))
        val resumed = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        failure(
            stepBatch(
                f,
                resumed,
                actor.copy(permissions = actor.permissions - "leave.accrual.post"),
            ),
            "access_denied",
        )
        drainBatch(f, resumed)
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun concurrentStepsWithOneLeaseAreSerializedAndCannotRepeatAnEmployee() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val actor = accountingActor(f)
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                (1..2).map {
                    pool.submit<Result<JobStep>> {
                        gate.await(5, TimeUnit.SECONDS)
                        stepBatch(f, lease, actor)
                    }
                }
            gate.countDown()
            val completed = pending.map { it.get(15, TimeUnit.SECONDS) }
            completed.forEach { assertTrue(it is Result.Success, it.toString()) }
            assertEquals(
                listOf(1, 2),
                completed.map { (it as Result.Success).value.completedItems }.sorted(),
            )
        }
        assertEquals(2, accountingRows(f, "leave_batch_results"))
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
        assertEquals(Result.Success(JobStep(3, true)), stepBatch(f, lease))
    }

    @Test
    fun onlyTheOriginalAuthorCanResumeAndAttemptHistoryHasAFiniteLimit() {
        val f = accountingFixture()
        var lease = beginBatch(f)
        val id = batchId(lease)
        for (permission in listOf("leave.accrual.post", "leave.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                f.managerAccount,
                permission,
            )
        for (attempt in 1..8) {
            cancelBatch(f, lease)
            val current = batchView(f, id)
            val version = current["batch"]["version"].asLong()
            if (attempt == 1)
                accountingCode(
                    resumeBatch(f, id, version, client = f.supervisor, csrf = f.supervisorCsrf),
                    403,
                    "leave_batch_resume_owner_required",
                )
            if (attempt < 8) {
                accountingBody(resumeBatch(f, id, version))
                lease = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
            } else accountingCode(resumeBatch(f, id, version), 409, "leave_batch_attempt_limit")
        }
        assertEquals(8, batchView(f, id)["attempts"].size())
        assertEquals(0, accountingRows(f, "leave_ledger"))
    }

    @Test
    fun credentialRevocationWhileAWorkerWaitsCannotPublishTheNextGrant() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val actor = accountingActor(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { stepBatch(f, lease, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        actor.accountId,
                    )
                barrier.release.countDown()
                val denied = pending.get(10, TimeUnit.SECONDS)
                failure(denied, "session_revoked")
                assertEquals(
                    Result.Success(Unit),
                    abortBatch.execute(lease, (denied as Result.Failed).failure),
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, accountingRows(f, "leave_accrual_postings"))
        assertEquals(0, accountingRows(f, "leave_batch_results"))
        assertEquals("FAILED", jobStatus(lease))
    }
}
