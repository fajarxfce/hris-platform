package dev.fajar.hris.people.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OffboardingSchedulePolicyTest {
    private val company = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val now = Instant.parse("2026-09-30T08:00:00Z")
    private val lastDay = LocalDate.of(2026, 9, 30)
    private val employee =
        Employee(
            UUID.randomUUID(),
            company,
            "EMP-001",
            PersonProfile(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Schedule fixture",
                null,
                "ID",
                null,
            ),
            EmploymentTerms(
                LocalDate.of(2026, 1, 1),
                ContractKind.PERMANENT,
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
            ),
            null,
            4,
            3,
        )
    private val case =
        LifecycleCase(
            UUID.randomUUID(),
            employee.id,
            LifecycleKind.OFFBOARDING,
            lastDay,
            UUID.randomUUID(),
            0,
            "Departure",
            LifecycleStatus.OPEN,
            2,
            author,
            now,
            listOf(
                LifecycleTask(
                    "equipment",
                    "Equipment reviewed",
                    true,
                    lastDay,
                    author,
                    LifecycleTaskStatus.DONE,
                    author,
                    now,
                )
            ),
        )
    private val window = offboardingWindow(lastDay, "Asia/Jakarta", now)
    private val schedule =
        OffboardingSchedule(
            UUID.randomUUID(),
            case.id,
            employee.id,
            case.version,
            employee.version,
            employee.appliedRevision,
            employee.person.accountId,
            lastDay,
            window.companyTimezone,
            window.scheduledFor,
            window.validUntil,
            1,
            UUID.randomUUID(),
            author,
            now,
            "Reviewed departure",
        )
    private val permissions = setOf("people.manage", "people.offboard", "people.lifecycle.manage")
    private val actor =
        Actor(author, company, permissions, now, UUID.randomUUID(), credentialVersion = 7)

    private fun details(
        status: OffboardingScheduleStatus = OffboardingScheduleStatus.SCHEDULED,
        jobStatus: JobStatus = JobStatus.QUEUED,
    ) =
        OffboardingScheduleDetails(
            schedule.copy(status = status),
            LifecycleScheduleJob(schedule.jobId, jobStatus, 1, 1, false, 0, null, null),
        )

    private fun code(result: Result<*>) = (result as Result.Failed).failure.code

    @Test
    fun executionStartsAfterTheInclusiveLastWorkingDayInTheCompanyTimezone() {
        assertEquals(Instant.parse("2026-09-30T17:00:00Z"), window.scheduledFor)
        assertEquals(Instant.parse("2026-10-07T17:00:00Z"), window.validUntil)
        assertEquals(LocalDate.of(2026, 9, 30), window.today)
        assertTrue(offboardingScheduleBlockers(case, window, now, emptyList()).isEmpty())
        assertEquals(
            "offboarding_schedule_not_future",
            offboardingScheduleBlockers(case, window, window.scheduledFor, emptyList()).single(),
        )
    }

    @Test
    fun daylightSavingBoundariesUseTheCompanyCalendarRatherThanAddingOneUtcDay() {
        val spring =
            offboardingWindow(
                LocalDate.of(2026, 3, 28),
                "Europe/Paris",
                Instant.parse("2026-03-28T08:00:00Z"),
            )
        val autumn =
            offboardingWindow(
                LocalDate.of(2026, 10, 24),
                "Europe/Paris",
                Instant.parse("2026-10-24T08:00:00Z"),
            )
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), spring.scheduledFor)
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), autumn.scheduledFor)
        assertEquals(
            Duration.ofHours(168),
            Duration.between(autumn.scheduledFor, autumn.validUntil),
        )
    }

    @Test
    fun repeatedMidnightMatchesTheStoredLaterOffset() {
        val repeated = offboardingWindow(LocalDate.of(2026, 10, 31), "America/Havana", now)
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), repeated.scheduledFor)
        val missing = offboardingWindow(LocalDate.of(2026, 3, 7), "America/Havana", now)
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), missing.scheduledFor)
    }

    @Test
    fun schedulingIsBoundedAndATerminalJobNeedsItsPendingAuthorizationClosed() {
        val far = case.copy(targetDate = window.today.plusDays(366))
        assertTrue(
            offboardingScheduleBlockers(
                    far,
                    offboardingWindow(far.targetDate, "Asia/Jakarta", now),
                    now,
                    emptyList(),
                )
                .contains("offboarding_schedule_too_far")
        )
        assertEquals(
            "offboarding_schedule_active",
            offboardingScheduleBlockers(
                    case,
                    window,
                    now,
                    listOf(details(jobStatus = JobStatus.FAILED)),
                )
                .single(),
        )
        assertEquals(
            "offboarding_schedule_limit",
            offboardingScheduleBlockers(
                    case,
                    window,
                    now,
                    List(8) { details(OffboardingScheduleStatus.FAILED, JobStatus.FAILED) },
                )
                .single(),
        )
        assertTrue(
            offboardingScheduleBlockers(
                    case,
                    window,
                    now,
                    listOf(details(OffboardingScheduleStatus.FAILED, JobStatus.FAILED)),
                )
                .isEmpty()
        )
    }

    @Test
    fun reviewBindsAccountLinkageAppliedRevisionCompanyAndTimezoneWithoutNames() {
        val basis = offboardingBasisRevision(case, employee, window)
        assertTrue(basis.matches(Regex("[a-f0-9]{64}")))
        for (changed in
            listOf(
                employee.copy(companyId = UUID.randomUUID()),
                employee.copy(person = employee.person.copy(accountId = null)),
                employee.copy(person = employee.person.copy(accountId = UUID.randomUUID())),
                employee.copy(version = 5),
                employee.copy(appliedRevision = 2),
            )) {
            assertNotEquals(basis, offboardingBasisRevision(case, changed, window))
        }
        assertNotEquals(basis, offboardingBasisRevision(case.copy(version = 3), employee, window))
        assertNotEquals(
            basis,
            offboardingBasisRevision(
                case,
                employee,
                window.copy(companyTimezone = "Asia/Pontianak"),
            ),
        )
        assertEquals(
            basis,
            offboardingBasisRevision(
                case,
                employee.copy(person = employee.person.copy(legalName = "Renamed")),
                window,
            ),
        )
    }

    @Test
    fun readinessIncludesOptionalTaskOutcomesAndExcludesSelfOffboarding() {
        assertTrue(offboardingBasisBlockers(case, employee, author).isEmpty())
        assertTrue(
            offboardingBasisBlockers(case, employee, employee.person.accountId!!)
                .contains("cannot_complete_own_offboarding")
        )
        val pending =
            case.tasks
                .single()
                .copy(
                    required = false,
                    status = LifecycleTaskStatus.PENDING,
                    completedAt = null,
                    completedBy = null,
                )
        assertTrue(
            offboardingBasisBlockers(case.copy(tasks = listOf(pending)), employee, author)
                .contains("lifecycle_tasks_unresolved")
        )
        assertTrue(
            offboardingBasisBlockers(
                    case.copy(tasks = listOf(pending.copy(required = true))),
                    employee,
                    author,
                )
                .contains("required_lifecycle_tasks_pending")
        )
        assertTrue(
            offboardingBasisBlockers(
                    case.copy(tasks = listOf(pending.copy(status = LifecycleTaskStatus.WAIVED))),
                    employee,
                    author,
                )
                .isEmpty()
        )
    }

    @Test
    fun aLateButAdmittedExecutionUsesTodayWithoutBackdatingEmployment() {
        assertEquals(
            Result.Success(LocalDate.of(2026, 10, 1)),
            validateOffboardingExecution(
                schedule,
                case,
                employee,
                "Asia/Jakarta",
                window.scheduledFor,
            ),
        )
        assertEquals(
            Result.Success(LocalDate.of(2026, 10, 3)),
            validateOffboardingExecution(
                schedule,
                case,
                employee,
                "Asia/Jakarta",
                window.scheduledFor.plus(Duration.ofDays(2)),
            ),
        )
    }

    @Test
    fun anExecutionOutsideItsWindowRequiresExplicitReview() {
        assertEquals(
            "offboarding_schedule_not_due",
            code(
                validateOffboardingExecution(
                    schedule,
                    case,
                    employee,
                    "Asia/Jakarta",
                    window.scheduledFor.minusSeconds(1),
                )
            ),
        )
        assertEquals(
            "offboarding_schedule_review_required",
            code(
                validateOffboardingExecution(
                    schedule,
                    case,
                    employee,
                    "Asia/Jakarta",
                    window.validUntil,
                )
            ),
        )
    }

    @Test
    fun changedCaseEmploymentEffectiveRevisionOrTimezoneRejectsTheRetainedIntent() {
        for ((changedCase, changedEmployee) in
            listOf(
                case.copy(version = 3) to employee,
                case to employee.copy(version = 5),
                case to employee.copy(appliedRevision = 4),
                case.copy(targetDate = lastDay.plusDays(1)) to employee,
            )) {
            assertEquals(
                "offboarding_schedule_changed",
                code(
                    validateOffboardingExecution(
                        schedule,
                        changedCase,
                        changedEmployee,
                        "Asia/Jakarta",
                        window.scheduledFor,
                    )
                ),
            )
        }
        assertEquals(
            "offboarding_schedule_changed",
            code(
                validateOffboardingExecution(
                    schedule,
                    case,
                    employee,
                    "Asia/Pontianak",
                    window.scheduledFor,
                )
            ),
        )
    }

    @Test
    fun newlyLinkedOrReplacedAccountsCannotInheritAnEarlierAuthorization() {
        assertEquals(
            "offboarding_identity_changed",
            code(
                validateOffboardingExecution(
                    schedule,
                    case,
                    employee.copy(person = employee.person.copy(accountId = UUID.randomUUID())),
                    "Asia/Jakarta",
                    window.scheduledFor,
                )
            ),
        )
        assertEquals(
            "offboarding_identity_changed",
            code(
                validateOffboardingExecution(
                    schedule.copy(employeeAccountId = null),
                    case,
                    employee,
                    "Asia/Jakarta",
                    window.scheduledFor,
                )
            ),
        )
    }

    @Test
    fun changedEmploymentTermsAndClosedCasesCannotBeCompleted() {
        assertTrue(
            offboardingBasisBlockers(
                    case,
                    employee.copy(terms = employee.terms.copy(endDate = lastDay.minusDays(1))),
                    author,
                )
                .contains("offboarding_terms_changed")
        )
        assertTrue(
            offboardingBasisBlockers(
                    case,
                    employee.copy(terms = employee.terms.copy(status = EmploymentStatus.ENDED)),
                    author,
                )
                .contains("offboarding_terms_changed")
        )
        assertTrue(
            offboardingBasisBlockers(
                    case.copy(status = LifecycleStatus.CANCELLED),
                    employee,
                    author,
                )
                .contains("lifecycle_case_not_open")
        )
    }

    @Test
    fun aWorkerMustOwnTheAdmittedCompanyAccountCredentialAndJobKind() {
        val request =
            JobRequest(
                schedule.jobId,
                company,
                author,
                JobKind.PEOPLE_OFFBOARD,
                UUID.randomUUID(),
                mapOf("scheduleId" to schedule.id.toString()),
                now,
                7,
                actor.correlationId,
                now,
                1,
                scheduledFor = window.scheduledFor,
            )
        val job = BackgroundJob(request, JobStatus.RUNNING, 1, false, 0, emptyMap(), null, null, 1)
        val lease = JobLease(job, UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(60))
        assertEquals(Result.Success(Unit), requireOffboardingJobActor(actor, lease))
        for (changed in
            listOf(
                actor.copy(accountId = UUID.randomUUID()),
                actor.copy(companyId = UUID.randomUUID()),
                actor.copy(credentialVersion = null),
                actor.copy(credentialVersion = 8),
            )) {
            assertEquals("job_scope_mismatch", code(requireOffboardingJobActor(changed, lease)))
        }
        assertEquals(
            "job_scope_mismatch",
            code(
                requireOffboardingJobActor(
                    actor,
                    lease.copy(
                        job = job.copy(request = request.copy(kind = JobKind.ANNOUNCEMENT_PUBLISH))
                    ),
                )
            ),
        )
        assertEquals(
            "offboarding_access_required",
            code(
                requireOffboardingJobActor(
                    actor.copy(permissions = permissions - "people.offboard"),
                    lease,
                )
            ),
        )
    }

    @Test
    fun cancellationAndRecoveryHintsFollowBothAuthorizationAndJobState() {
        assertEquals(setOf("cancel"), offboardingScheduleActions(actor, details()))
        assertTrue(
            offboardingScheduleActions(
                    actor,
                    details().let { it.copy(job = it.job.copy(cancellationRequested = true)) },
                )
                .isEmpty()
        )
        assertEquals(
            setOf("recover"),
            offboardingScheduleActions(actor, details(jobStatus = JobStatus.FAILED)),
        )
        assertTrue(
            offboardingScheduleActions(
                    actor,
                    details(OffboardingScheduleStatus.FAILED, JobStatus.FAILED),
                )
                .isEmpty()
        )
        assertTrue(
            offboardingScheduleActions(actor.copy(permissions = emptySet()), details()).isEmpty()
        )
    }

    @Test
    fun aCommittedCancellationAllowsManualClosureWhileWorkerCleanupIsUnavailable() {
        assertTrue(offboardingExecutionPending(listOf(details())))
        assertFalse(
            offboardingExecutionPending(
                listOf(details().let { it.copy(job = it.job.copy(cancellationRequested = true)) })
            )
        )
        assertFalse(offboardingExecutionPending(listOf(details(jobStatus = JobStatus.FAILED))))
        assertFalse(offboardingExecutionPending(emptyList()))
    }
}
