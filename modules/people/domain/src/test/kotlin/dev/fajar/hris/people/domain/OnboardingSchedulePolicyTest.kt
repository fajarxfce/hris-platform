package dev.fajar.hris.people.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OnboardingSchedulePolicyTest {
    private val company = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val now = Instant.parse("2026-10-15T08:00:00Z")
    private val firstDay = LocalDate.of(2026, 11, 2)
    private val employee =
        Employee(
            UUID.randomUUID(),
            company,
            "EMP-001",
            PersonProfile(UUID.randomUUID(), null, "Incoming employee", null, "ID", null),
            EmploymentTerms(
                firstDay,
                ContractKind.PERMANENT,
                firstDay,
                null,
                EmploymentStatus.PROBATION,
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
    private val template =
        LifecycleTemplate(
            UUID.randomUUID(),
            "JOINING",
            "Joining",
            LifecycleKind.ONBOARDING,
            true,
            2,
            listOf(
                LifecycleTaskDefinition("equipment", "Prepare equipment", true, -1),
                LifecycleTaskDefinition("intro", "Team introduction", false, 1),
            ),
        )
    private val selection =
        OnboardingScheduleSelection(template.id, firstDay, firstDay.minusDays(2))
    private val window = onboardingWindow(selection, "Asia/Jakarta", now)
    private val schedule =
        OnboardingSchedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            employee.id,
            employee.version,
            employee.appliedRevision,
            selection,
            template,
            mapOf("equipment" to author),
            window.companyTimezone,
            window.scheduledFor,
            window.validUntil,
            1,
            UUID.randomUUID(),
            author,
            now,
            "Prepare joining",
        )
    private val actor =
        Actor(
            author,
            company,
            setOf("people.lifecycle.manage"),
            now,
            UUID.randomUUID(),
            credentialVersion = 7,
        )

    private fun code(result: Result<*>) = (result as Result.Failed).failure.code

    private fun details(
        status: OnboardingScheduleStatus = OnboardingScheduleStatus.SCHEDULED,
        jobStatus: JobStatus = JobStatus.QUEUED,
    ) =
        OnboardingScheduleDetails(
            schedule.copy(status = status),
            LifecycleScheduleJob(schedule.jobId, jobStatus, 1, 1, false, 0, null, null),
        )

    @Test
    fun preparationStartsAtTheChosenCompanyMidnightAndStopsAfterTheFirstWorkingDay() {
        assertEquals(Instant.parse("2026-10-30T17:00:00Z"), window.scheduledFor)
        assertEquals(Instant.parse("2026-11-02T17:00:00Z"), window.validUntil)
        val early =
            onboardingWindow(selection.copy(startsOn = firstDay.minusDays(10)), "Asia/Jakarta", now)
        assertEquals(Duration.ofHours(168), Duration.between(early.scheduledFor, early.validUntil))
        assertTrue(early.validUntil < firstDay.atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant())
    }

    @Test
    fun firstDayWindowRespectsBothDaylightSavingTransitions() {
        val spring = LocalDate.of(2026, 3, 29)
        val autumn = LocalDate.of(2026, 10, 25)
        val springWindow =
            onboardingWindow(
                selection.copy(targetDate = spring, startsOn = spring),
                "Europe/Paris",
                now,
            )
        val autumnWindow =
            onboardingWindow(
                selection.copy(targetDate = autumn, startsOn = autumn),
                "Europe/Paris",
                now,
            )
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), springWindow.scheduledFor)
        assertEquals(
            Duration.ofHours(23),
            Duration.between(springWindow.scheduledFor, springWindow.validUntil),
        )
        assertEquals(
            Duration.ofHours(25),
            Duration.between(autumnWindow.scheduledFor, autumnWindow.validUntil),
        )
    }

    @Test
    fun repeatedMidnightUsesTheLaterOffsetWhileMissingMidnightUsesTheNextValidInstant() {
        val repeated = LocalDate.of(2026, 11, 1)
        val atOverlap =
            onboardingWindow(
                selection.copy(startsOn = repeated, targetDate = repeated),
                "America/Havana",
                now,
            )
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), atOverlap.scheduledFor)
        val deadline =
            onboardingWindow(
                selection.copy(
                    startsOn = repeated.minusDays(2),
                    targetDate = repeated.minusDays(1),
                ),
                "America/Havana",
                now,
            )
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), deadline.validUntil)
        val missing = LocalDate.of(2026, 3, 8)
        val atGap =
            onboardingWindow(
                selection.copy(startsOn = missing, targetDate = missing),
                "America/Havana",
                now,
            )
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), atGap.scheduledFor)
    }

    @Test
    fun invalidDatesDoNotEnterRepositoryWork() {
        assertTrue(validateOnboardingSelection(selection) is Result.Success)
        for (invalid in
            listOf(
                selection.copy(startsOn = firstDay.plusDays(1)),
                selection.copy(targetDate = LocalDate.of(2201, 1, 1)),
                selection.copy(startsOn = LocalDate.of(1899, 12, 31)),
            )) assertEquals(
            "invalid_onboarding_schedule",
            code(validateOnboardingSelection(invalid)),
        )
    }

    @Test
    fun admissionRequiresFuturePreparationAndTheExactFirstDayWithAnActiveOnboardingTemplate() {
        assertTrue(
            onboardingScheduleBlockers(employee, template, selection, window, now, emptyList())
                .isEmpty()
        )
        assertTrue(
            onboardingScheduleBlockers(
                    employee,
                    template,
                    selection,
                    window,
                    window.scheduledFor,
                    emptyList(),
                )
                .contains("onboarding_schedule_not_future")
        )
        assertTrue(
            onboardingScheduleBlockers(
                    employee.copy(terms = employee.terms.copy(startDate = firstDay.minusDays(1))),
                    template,
                    selection,
                    window,
                    now,
                    emptyList(),
                )
                .contains("onboarding_terms_changed")
        )
        for (invalid in
            listOf(
                template.copy(active = false),
                template.copy(kind = LifecycleKind.OFFBOARDING),
            )) assertTrue(
            onboardingScheduleBlockers(employee, invalid, selection, window, now, emptyList())
                .contains("onboarding_template_unavailable")
        )
        val distant = selection.copy(targetDate = window.today.plusDays(366))
        assertTrue(
            onboardingScheduleBlockers(employee, template, distant, window, now, emptyList())
                .contains("onboarding_schedule_too_far")
        )
    }

    @Test
    fun stoppedJobsNeedExplicitRecoveryAndAttemptsAreBounded() {
        assertEquals(
            listOf("onboarding_schedule_active"),
            onboardingScheduleBlockers(
                employee,
                template,
                selection,
                window,
                now,
                listOf(details(jobStatus = JobStatus.FAILED)),
            ),
        )
        assertEquals(
            listOf("onboarding_schedule_limit"),
            onboardingScheduleBlockers(
                employee,
                template,
                selection,
                window,
                now,
                List(8) { details(OnboardingScheduleStatus.CANCELLED, JobStatus.CANCELLED) },
            ),
        )
        assertTrue(
            onboardingScheduleBlockers(
                    employee,
                    template,
                    selection,
                    window,
                    now,
                    listOf(details(OnboardingScheduleStatus.CANCELLED, JobStatus.CANCELLED)),
                )
                .isEmpty()
        )
    }

    @Test
    fun reviewBindsSourceRevisionsAndDatesWithoutDependingOnDisplayNames() {
        val basis = onboardingBasisRevision(employee, template, selection, window)
        assertTrue(basis.matches(Regex("[a-f0-9]{64}")))
        assertEquals(
            basis,
            onboardingBasisRevision(
                employee.copy(person = employee.person.copy(legalName = "Renamed")),
                template,
                selection,
                window,
            ),
        )
        for (changed in
            listOf(
                employee.copy(companyId = UUID.randomUUID()),
                employee.copy(id = UUID.randomUUID()),
                employee.copy(version = 5),
                employee.copy(appliedRevision = 2),
            )) assertNotEquals(basis, onboardingBasisRevision(changed, template, selection, window))
        assertNotEquals(
            basis,
            onboardingBasisRevision(employee, template.copy(version = 3), selection, window),
        )
        assertNotEquals(
            basis,
            onboardingBasisRevision(employee, template, selection.copy(startsOn = firstDay), window),
        )
        assertNotEquals(
            basis,
            onboardingBasisRevision(
                employee,
                template,
                selection,
                window.copy(companyTimezone = "Asia/Pontianak"),
            ),
        )
    }

    @Test
    fun leaseExecutionRequiresUnchangedSourceVersionsAndAnOpenWindow() {
        assertTrue(
            validateOnboardingExecution(
                schedule,
                employee,
                template,
                window.companyTimezone,
                window.scheduledFor,
            )
                is Result.Success
        )
        assertEquals(
            "onboarding_schedule_not_due",
            code(
                validateOnboardingExecution(
                    schedule,
                    employee,
                    template,
                    window.companyTimezone,
                    window.scheduledFor.minusNanos(1),
                )
            ),
        )
        assertEquals(
            "onboarding_schedule_review_required",
            code(
                validateOnboardingExecution(
                    schedule,
                    employee,
                    template,
                    window.companyTimezone,
                    window.validUntil,
                )
            ),
        )
        for (changed in
            listOf(employee.copy(version = 5), employee.copy(appliedRevision = 2))) assertEquals(
            "onboarding_schedule_changed",
            code(
                validateOnboardingExecution(
                    schedule,
                    changed,
                    template,
                    window.companyTimezone,
                    window.scheduledFor,
                )
            ),
        )
        assertEquals(
            "onboarding_schedule_changed",
            code(
                validateOnboardingExecution(
                    schedule,
                    employee,
                    template.copy(version = 3),
                    window.companyTimezone,
                    window.scheduledFor,
                )
            ),
        )
        assertEquals(
            "onboarding_schedule_changed",
            code(
                validateOnboardingExecution(
                    schedule,
                    employee,
                    template,
                    "Asia/Pontianak",
                    window.scheduledFor,
                )
            ),
        )
        assertEquals(
            "onboarding_terms_changed",
            code(
                validateOnboardingExecution(
                    schedule,
                    employee.copy(terms = employee.terms.copy(status = EmploymentStatus.ENDED)),
                    template,
                    window.companyTimezone,
                    window.scheduledFor,
                )
            ),
        )
    }

    @Test
    fun initiationCreatesOnlyPendingTasksAndRetainsUnassignedTasks() {
        val created = scheduledOnboardingCase(schedule, window.scheduledFor)
        assertEquals(schedule.caseId, created.id)
        assertEquals(schedule.createdBy, created.createdBy)
        assertEquals(window.scheduledFor, created.createdAt)
        assertEquals(LifecycleStatus.OPEN, created.status)
        assertEquals(0L, created.version)
        assertEquals(
            listOf(firstDay.minusDays(1), firstDay.plusDays(1)),
            created.tasks.map { it.dueDate },
        )
        assertEquals(author, created.tasks[0].assigneeId)
        assertNull(created.tasks[1].assigneeId)
        assertTrue(
            created.tasks.all {
                it.status == LifecycleTaskStatus.PENDING &&
                    it.completedAt == null &&
                    it.completedBy == null
            }
        )
    }

    @Test
    fun readingNeedsAllThreeGrantsAndTerminalHeadersHaveNoActions() {
        val reader =
            actor.copy(
                permissions = actor.permissions + setOf("people.read", "people.lifecycle.read")
            )
        assertTrue(requireOnboardingAccess(reader, reading = true) is Result.Success)
        for (permission in reader.permissions) assertTrue(
            requireOnboardingAccess(
                reader.copy(permissions = reader.permissions - permission),
                reading = true,
            )
                is Result.Failed
        )
        assertTrue(requireOnboardingAccess(actor.copy(companyId = null)) is Result.Failed)
        assertEquals(setOf("cancel"), onboardingScheduleActions(actor, details()))
        assertEquals(
            setOf("recover"),
            onboardingScheduleActions(actor, details(jobStatus = JobStatus.FAILED)),
        )
        assertTrue(
            onboardingScheduleActions(
                    actor,
                    details(OnboardingScheduleStatus.STARTED, JobStatus.SUCCEEDED),
                )
                .isEmpty()
        )
        val cancelling = details().let { it.copy(job = it.job.copy(cancellationRequested = true)) }
        assertTrue(onboardingScheduleActions(actor, cancelling).isEmpty())
    }

    @Test
    fun jobAuthorityMustMatchItsCompanyActorCredentialAndFixedRequest() {
        val request =
            JobRequest(
                schedule.jobId,
                company,
                author,
                JobKind.PEOPLE_ONBOARD_START,
                UUID.randomUUID(),
                mapOf("scheduleId" to schedule.id.toString()),
                now,
                7,
                UUID.randomUUID(),
                now,
                1,
                scheduledFor = window.scheduledFor,
            )
        val job = BackgroundJob(request, JobStatus.RUNNING, 1, false, 0, emptyMap(), null, null, 1)
        val lease = JobLease(job, UUID.randomUUID(), UUID.randomUUID(), window.validUntil)
        assertTrue(requireOnboardingJobActor(actor, lease) is Result.Success)
        for (changed in
            listOf(
                request.copy(kind = JobKind.PEOPLE_OFFBOARD),
                request.copy(companyId = UUID.randomUUID()),
                request.copy(actorId = UUID.randomUUID()),
                request.copy(credentialVersion = 8),
                request.copy(totalItems = 2),
                request.copy(values = request.values + ("untrusted" to "value")),
            )) assertEquals(
            "job_scope_mismatch",
            code(requireOnboardingJobActor(actor, lease.copy(job = job.copy(request = changed)))),
        )
    }
}
