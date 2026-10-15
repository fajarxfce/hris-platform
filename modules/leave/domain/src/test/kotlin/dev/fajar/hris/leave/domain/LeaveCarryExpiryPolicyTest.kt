package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.policies.availableJobActions
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCarryExpiryPolicyTest {
    private val created = Instant.parse("2027-01-05T03:00:00Z")
    private val validity =
        LeaveCarryValidity(LocalDate.of(2027, 3, 31), Instant.parse("2027-03-31T17:00:00Z"))
    private val employee = UUID.randomUUID()
    private val company = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val type = UUID.randomUUID()
    private val batch = UUID.randomUUID()
    private val closing =
        LeaveYearClosing(
            UUID.randomUUID(),
            employee,
            type,
            2026,
            1,
            6,
            0,
            0,
            LeaveYearRollover(6, 0),
            LeavePolicySnapshot(
                type,
                "ANNUAL",
                0,
                LeavePolicy(
                    "Annual leave",
                    true,
                    true,
                    0,
                    emptySet(),
                    30,
                    accrual = LeaveAccrualPolicy(LeaveAccrualFrequency.MANUAL, 0, 6, 3),
                ),
            ),
            actor,
            created,
            "Year closing",
            true,
            "Asia/Jakarta",
            batch,
        )
    private val bucket = LeaveCarryBucket(closing.id, 2027, 6, 6, validity = validity)
    private val expiry =
        LeaveCarryExpiry(
            UUID.randomUUID(),
            LeaveCarryExpirySource.Batch(batch),
            type,
            2027,
            "Asia/Jakarta",
            validity,
            actor,
            created,
            "Year closing",
            5000,
            UUID.randomUUID(),
        )
    private val request =
        JobRequest(
            expiry.jobId,
            company,
            actor,
            JobKind.LEAVE_CARRY_EXPIRY,
            UUID.randomUUID(),
            mapOf("expiryId" to expiry.id.toString()),
            created,
            0,
            UUID.randomUUID(),
            created,
            5001,
            scheduledFor = validity.expiresAt,
        )
    private val job =
        BackgroundJob(request, JobStatus.RUNNING, 1, false, 0, emptyMap(), null, null, 1)
    private val attempt = LeaveCarryExpiryAttempt(request.id, 1, 0, created, "Year closing")

    @Test
    fun expiryStartsAtItsExactFrozenBoundary() {
        assertEquals(
            "leave_carry_expiry_not_due",
            (validateLeaveCarryExpiryProgress(
                    expiry,
                    job,
                    attempt,
                    0,
                    validity.expiresAt.minusNanos(1),
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            Result.Success(Unit),
            validateLeaveCarryExpiryProgress(expiry, job, attempt, 0, validity.expiresAt),
        )
    }

    @Test
    fun anotherClosingOrChangedCalendarCannotBelongToTheOriginalCohort() {
        assertTrue(belongsToLeaveCarryExpiry(expiry, employee, closing, bucket))
        assertFalse(
            belongsToLeaveCarryExpiry(
                expiry,
                employee,
                closing.copy(batchId = UUID.randomUUID()),
                bucket,
            )
        )
        assertFalse(
            belongsToLeaveCarryExpiry(expiry, employee, closing.copy(timezone = "UTC"), bucket)
        )
        assertFalse(
            belongsToLeaveCarryExpiry(
                expiry,
                employee,
                closing,
                bucket.copy(validity = validity.copy(expiresAt = validity.expiresAt.plusSeconds(1))),
            )
        )
        assertFalse(belongsToLeaveCarryExpiry(expiry, UUID.randomUUID(), closing, bucket))
        val direct =
            expiry.copy(
                source = LeaveCarryExpirySource.Closing(closing.id, employee),
                totalEmployees = 1,
            )
        assertTrue(
            belongsToLeaveCarryExpiry(direct, employee, closing.copy(batchId = null), bucket)
        )
        assertFalse(belongsToLeaveCarryExpiry(direct, employee, closing, bucket))
    }

    @Test
    fun recoveryResumesOnlyItsRetainedPrefixAndKeepsOneFinalizationStep() {
        val id = UUID.randomUUID()
        val resumed = expiry.copy(jobId = id, version = 1)
        val second = attempt.copy(jobId = id, attempt = 2, baseCompleted = 4999)
        val current = job.copy(request = request.copy(id = id, totalItems = 2), completedItems = 1)
        assertEquals(
            Result.Success(Unit),
            validateLeaveCarryExpiryCheckpoint(resumed, current, second, 5000),
        )
        assertTrue(
            validateLeaveCarryExpiryCheckpoint(
                resumed,
                current.copy(completedItems = 0),
                second,
                5000,
            )
                is Result.Failed
        )
        assertTrue(
            validateLeaveCarryExpiryCheckpoint(
                resumed,
                current,
                second.copy(baseCompleted = 5000),
                5000,
            )
                is Result.Failed
        )
        assertTrue(
            validateLeaveCarryExpiryCheckpoint(resumed, current, attempt, 5000) is Result.Failed
        )
    }

    @Test
    fun changedScopeDeadlineOrUnboundedWorkCannotReuseAJob() {
        for (changed in
            listOf(
                request.copy(kind = JobKind.LEAVE_YEAR_CLOSE),
                request.copy(totalItems = 5000),
                request.copy(values = mapOf("expiryId" to UUID.randomUUID().toString())),
                request.copy(scheduledFor = validity.expiresAt.minusSeconds(1)),
                request.copy(scheduledFor = null),
            )) assertTrue(
            validateLeaveCarryExpiryCheckpoint(expiry, job.copy(request = changed), attempt, 0)
                is Result.Failed
        )
        assertTrue(
            validateLeaveCarryExpiryCheckpoint(expiry.copy(totalEmployees = 5001), job, attempt, 0)
                is Result.Failed
        )
        assertTrue(validateLeaveCarryExpiryCheckpoint(expiry, job, attempt, -1) is Result.Failed)
    }

    @Test
    fun fixedExpiryHasNoCancellationActionEvenForAJobManager() {
        val manager = Actor(actor, company, setOf("jobs.manage"), created, UUID.randomUUID())
        assertTrue(availableJobActions(manager, job).isEmpty())
        assertEquals(
            listOf("cancel"),
            availableJobActions(
                manager,
                job.copy(request = request.copy(kind = JobKind.DOCUMENT_INVENTORY)),
            ),
        )
    }
}
