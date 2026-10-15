package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.jobs.domain.usecases.RequestJobCancellation
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveCarryRepository
import dev.fajar.hris.leave.domain.usecases.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class LeaveCarryExpiryExecutionTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var advanceExpiry: AdvanceLeaveCarryExpiry
    @Autowired private lateinit var abortExpiry: AbortLeaveCarryExpiry
    @Autowired private lateinit var resumeExpiry: ResumeLeaveCarryExpiry
    @Autowired private lateinit var expiryJournal: ChangeJournalRepository
    @Autowired private lateinit var cancelJob: RequestJobCancellation

    private fun origin(c: PreparedCarry): LeaveCarryExpiry =
        success(
                transactions.run(accountingActor(c.f)) { carryExpiries.list(c.f.company, null, 10) }
            )
            .items
            .single()

    private fun job(c: PreparedCarry, id: UUID): BackgroundJob =
        success(transactions.run(accountingActor(c.f)) { carryExpiryJobs.find(c.f.company, id) })!!

    /** Keep a real database-time lease while the policy clock tests a future calendar boundary. */
    private fun activate(c: PreparedCarry, id: UUID = origin(c).jobId): JobLease {
        val owner = UUID.randomUUID()
        val token = UUID.randomUUID()
        assertEquals(
            1,
            database()
                .update(
                    """update background_jobs set status='RUNNING',attempts=attempts+1,
            lease_owner=?,lease_token=?,lease_until=clock_timestamp()+interval '120 seconds',version=version+1
            where company_id=? and id=? and status='QUEUED'""",
                    owner,
                    token,
                    c.f.company,
                    id,
                ),
        )
        return JobLease(job(c, id), owner, token, Instant.now().plusSeconds(120))
    }

    private fun results(c: PreparedCarry): List<LeaveCarryExpiryOutcome> {
        val id = origin(c).id
        return success(
                transactions.run(accountingActor(c.f)) {
                    carryExpiries.results(c.f.company, id, null, 10)
                }
            )
            .items
    }

    private fun retained(c: PreparedCarry): Map<String, Any> = buildMap {
        for (table in
            listOf(
                "leave_ledger",
                "leave_carry_movements",
                "leave_carry_expiry_outcomes",
                "mobile_sync_changes",
                "audit_entries",
                "outbox_events",
            )) put(table, accountingRows(c.f, table))
        put("bucket", storedBucket(c))
        put("balance", storedBalance(c))
        put("job", job(c, origin(c).jobId))
    }

    @Test
    fun missingDurableOriginRejectsTheEntireAwardAndCanRetry() {
        val c = prepareCarry()
        val before =
            listOf("leave_year_closings", "leave_ledger", "leave_accounts", "mobile_sync_changes")
                .associateWith { accountingRows(c.f, it) }
        assertTrue(storeCarry(c, includeExpiry = false) is Result.Failed)
        assertEquals(before, before.keys.associateWith { accountingRows(c.f, it) })
        success(storeCarry(c))
        assertEquals(carryDeadline, job(c, origin(c).jobId).request.scheduledFor)
    }

    @Test
    fun publicClosingAdmitsOneJobAndRetainsItsReceiptWithoutDuplication() {
        val c = prepareCarry()
        val body = closingBody(c.closing.id, c.closing.sourceVersion, c.closing.policy.revision)
        val operation = UUID.randomUUID()
        val receipt = accountingBody(closeYear(c.f, body, operation))
        val expiry = origin(c)
        assertEquals(LeaveCarryExpirySource.Closing(c.closing.id, c.f.employee), expiry.source)
        assertEquals(1, expiry.totalEmployees)
        assertEquals(carryDeadline, expiry.validity.expiresAt)
        assertEquals(1, accountingRows(c.f, "background_jobs"))
        assertEquals(receipt, accountingBody(closeYear(c.f, body, operation)))
        assertEquals(1, accountingRows(c.f, "background_jobs"))
    }

    @Test
    fun earlyExecutionCannotWriteAndAtTheBoundaryEveryUnitIsSettledOnce() {
        val c = carried()
        assertTrue(claimDocuments(JobKind.LEAVE_CARRY_EXPIRY).isEmpty())
        val lease = activate(c)
        val before = retained(c)
        clock.set(carryDeadline.minusNanos(1))
        assertEquals(
            "leave_carry_expiry_not_due",
            (advanceExpiry.execute(lease) as Result.Failed).failure.code,
        )
        assertEquals(before, retained(c))
        clock.set(carryDeadline)
        assertEquals(JobStep(1, false), success(advanceExpiry.execute(lease)))
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertEquals(0, storedBalance(c).availableHalfDays)
        assertEquals(LeaveCarryExpiryOutcomeStatus.EXPIRED, results(c).single().status)
        assertEquals(JobStep(2, true), success(advanceExpiry.execute(lease)))
        assertEquals(JobStatus.SUCCEEDED, job(c, lease.job.request.id).status)
        assertEquals("job_lease_lost", (advanceExpiry.execute(lease) as Result.Failed).failure.code)
        assertEquals(1, results(c).size)
    }

    @Test
    fun reservedUnitsRemainFundedAndTheirLateReturnExpiresInTheSameTransaction() {
        val c = carried()
        configureWorkAndApprovals(c.f)
        val request = success(reserveCarry(c))
        val lease = activate(c)
        clock.set(carryDeadline)
        success(advanceExpiry.execute(lease))
        success(advanceExpiry.execute(lease))
        assertEquals(4, results(c).single().halfDays)
        assertEquals(2, storedBucket(c).reservedHalfDays)
        success(finishCarry(c, request, LeaveBalanceEffect.RELEASE, carryDeadline.plusSeconds(1)))
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertEquals(0, storedBalance(c).availableHalfDays)
        assertEquals(0, storedBalance(c).reservedHalfDays)
    }

    @Test
    fun priorSettlementProducesAnUnchangedOutcomeWithoutAnotherDebit() {
        val c = carried()
        success(expiry(c))
        val lease = activate(c)
        clock.set(carryDeadline)
        val ledgerCount = accountingRows(c.f, "leave_ledger")
        success(advanceExpiry.execute(lease))
        success(advanceExpiry.execute(lease))
        assertEquals(LeaveCarryExpiryOutcomeStatus.UNCHANGED, results(c).single().status)
        assertEquals(ledgerCount, accountingRows(c.f, "leave_ledger"))
    }

    @Test
    fun lostLeaseOrChangedRequestCannotWriteAnOutcome() {
        val c = carried()
        val lease = activate(c)
        clock.set(carryDeadline)
        val before = retained(c)
        assertEquals(
            "job_lease_lost",
            (advanceExpiry.execute(lease.copy(token = UUID.randomUUID())) as Result.Failed)
                .failure
                .code,
        )
        val changed =
            lease.copy(job = lease.job.copy(request = lease.job.request.copy(values = emptyMap())))
        assertEquals(
            "job_scope_mismatch",
            (advanceExpiry.execute(changed) as Result.Failed).failure.code,
        )
        assertEquals(before, retained(c))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun failedOrMissingCheckpointRollsBackTheLedgerCarryOutcomeAndSyncTogether(
        acknowledged: Boolean
    ) {
        val c = carried()
        val lease = activate(c)
        clock.set(carryDeadline)
        val before = retained(c)
        val refusing =
            object : JobRepository by carryExpiryJobs {
                override fun checkpoint(lease: JobLease, progress: JobProgress): Result<Boolean> =
                    Result.Success(acknowledged)
            }
        val useCase =
            AdvanceLeaveCarryExpiry(
                carryExpiries,
                refusing,
                carryRepository,
                carryLedger,
                carryEntitlements,
                expiryJournal,
                transactions,
                clock,
            )
        val result = useCase.execute(lease)
        assertTrue(result is Result.Failed, result.toString())
        if (!acknowledged) assertEquals("job_lease_lost", (result as Result.Failed).failure.code)
        assertEquals(before, retained(c))
        success(advanceExpiry.execute(lease))
    }

    @Test
    fun cancellationAfterTheCarryWritePropagatesAndRollsBack() {
        val c = carried()
        val lease = activate(c)
        clock.set(carryDeadline)
        val before = retained(c)
        val interrupted =
            object : LeaveCarryRepository by carryRepository {
                override fun append(
                    companyId: UUID,
                    postings: List<LeaveCarryPosting>,
                ): Result<Unit> {
                    success(carryRepository.append(companyId, postings))
                    throw CancellationException("Fixture cancellation")
                }
            }
        val useCase =
            AdvanceLeaveCarryExpiry(
                carryExpiries,
                carryExpiryJobs,
                interrupted,
                carryLedger,
                carryEntitlements,
                expiryJournal,
                transactions,
                clock,
            )
        assertThrows(CancellationException::class.java) { useCase.execute(lease) }
        assertEquals(before, retained(c))
        success(advanceExpiry.execute(lease))
    }

    @Test
    fun originalCreatorRevocationDoesNotTurnFixedExpiryIntoPermanentCredit() {
        val c = carried()
        val lease = activate(c)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=?",
                c.f.company,
                c.closing.actorId,
            )
        clock.set(carryDeadline)
        success(advanceExpiry.execute(lease))
        success(advanceExpiry.execute(lease))
        assertEquals(6, storedBucket(c).expiredHalfDays)
    }

    @Test
    fun terminalRecoveryRetainsTheDeadlinePrefixAttemptsAndOriginalReceipt() {
        val c = carried()
        val lease = activate(c)
        clock.set(carryDeadline)
        success(advanceExpiry.execute(lease))
        success(
            abortExpiry.execute(lease, Failure(FailureKind.CONFLICT, "fixture_processing_stopped"))
        )
        val original = origin(c)
        val operation = UUID.randomUUID()
        val actor = accountingActor(c.f).copy(mfaVerifiedAt = clock.instant())
        val receipt =
            success(resumeExpiry.execute(actor, operation, original.id, 0, "Resume expiry"))
        val resumed = origin(c)
        assertEquals(original.copy(jobId = resumed.jobId, version = 1), resumed)
        assertEquals(1, receipt.version)
        assertEquals(1, job(c, resumed.jobId).request.totalItems)
        assertEquals(carryDeadline, job(c, resumed.jobId).request.scheduledFor)
        val attempts =
            success(transactions.run(actor) { carryExpiries.attempts(c.f.company, original.id) })
        assertEquals(listOf(0, 1), attempts.map { it.baseCompleted })
        assertTrue(
            success(resumeExpiry.execute(actor, operation, original.id, 0, "Resume expiry"))
                .replayed
        )
        success(advanceExpiry.execute(activate(c, resumed.jobId)))
        assertEquals(JobStatus.SUCCEEDED, job(c, resumed.jobId).status)
        assertEquals(JobStatus.FAILED, job(c, original.jobId).status)
        assertEquals(1, results(c).size)
    }

    @Test
    fun fixedExpiryCannotBeCancelledAndItsHistoryIsImmutable() {
        val c = carried()
        val expiry = origin(c)
        val actor = accountingActor(c.f).copy(mfaVerifiedAt = clock.instant())
        assertEquals(
            "job_cancellation_unavailable",
            (cancelJob.execute(actor, expiry.jobId, 0) as Result.Failed).failure.code,
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update background_jobs set cancellation_requested=true where company_id=? and id=?",
                    c.f.company,
                    expiry.jobId,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_carry_expiries set expires_at=expires_at+interval '1 day' where company_id=?",
                    c.f.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update("delete from leave_carry_expiry_attempts where company_id=?", c.f.company)
        }
    }

    @Test
    fun originsAttemptsAndOutcomesStayInsideTheirCompanyPartition() {
        val c = carried()
        val origin = origin(c)
        val other = accountingActor(c.f).copy(companyId = UUID.randomUUID())
        success(
            transactions.run(other) {
                assertNull(success(carryExpiries.find(c.f.company, origin.id)))
                assertNull(success(carryExpiries.forJob(c.f.company, origin.jobId)))
                assertTrue(success(carryExpiries.list(c.f.company, null, 10)).items.isEmpty())
                assertTrue(success(carryExpiries.attempts(c.f.company, origin.id)).isEmpty())
                assertTrue(
                    success(carryExpiries.results(c.f.company, origin.id, null, 10)).items.isEmpty()
                )
                Result.Success(Unit)
            }
        )
        assertTrue(
            transactions.run(other) {
                carryExpiries.create(
                    c.f.company,
                    origin.copy(id = UUID.randomUUID()),
                    LeaveCarryExpiryAttempt(origin.jobId, 1, 0, origin.createdAt, origin.reason),
                )
            } is Result.Failed
        )
        assertEquals(origin, origin(c))
    }

    @Test
    fun simultaneousRecoveryCannotCreateTwoCurrentAttempts() {
        val c = carried()
        val lease = activate(c)
        success(
            abortExpiry.execute(lease, Failure(FailureKind.CONFLICT, "fixture_processing_stopped"))
        )
        val original = origin(c)
        val actor = accountingActor(c.f).copy(mfaVerifiedAt = clock.instant())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                (1..2).map {
                    pool.submit<Result<MutationReceipt>> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        resumeExpiry.execute(
                            actor,
                            UUID.randomUUID(),
                            original.id,
                            0,
                            "Resume fixed expiry",
                        )
                    }
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                val results = pending.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it is Result.Success })
                assertEquals(
                    "stale_version",
                    (results.single { it is Result.Failed } as Result.Failed).failure.code,
                )
            } finally {
                start.countDown()
            }
        }
        assertEquals(1, origin(c).version)
        assertEquals(2, accountingRows(c.f, "background_jobs"))
        assertEquals(2, accountingRows(c.f, "leave_carry_expiry_attempts"))
    }
}
