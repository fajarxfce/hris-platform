package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class AttendanceAssuranceTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var attendance: AttendanceRepository
    @Autowired private lateinit var corrections: AttendanceCorrectionRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(strings = ["window", "capture", "review", "correction", "days", "history"])
    fun expiryWhileWaitingCannotAuthorizeAttendanceOrReplay(mode: String) =
        exerciseCompletion(mode, false)

    @ParameterizedTest
    @ValueSource(strings = ["window", "capture", "review", "correction", "days", "history"])
    fun expiryAfterAcquisitionCannotReleaseAttendanceDataOrCommands(mode: String) =
        exerciseCompletion(mode, true)

    @ParameterizedTest
    @ValueSource(strings = ["window", "capture", "review", "correction"])
    fun receiptCompletionStillRequiresProofAfterItsFinalAcquisition(mode: String) =
        exerciseCompletion(mode, true, true)

    private fun exerciseCompletion(mode: String, completion: Boolean, replay: Boolean = false) {
        val f = isolatedFixture()
        val self = mode in setOf("window", "capture")
        val account = if (self) f.employeeAccount else f.actor.accountId
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        val security = IdentitySecurityPolicy()
        val actor =
            f.actor.copy(
                accountId = account,
                permissions = if (self) setOf("attendance.self.record") else f.actor.permissions,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val operation = UUID.randomUUID()
        val id = UUID.randomUUID()
        val date = LocalDate.parse("2026-10-01")
        val device = UUID.randomUUID()
        val capture =
            AttendanceCapture(
                id,
                f.employee,
                date,
                AttendanceKind.CLOCK_IN,
                clock.instant(),
                device,
                null,
                true,
                null,
            )
        if (mode == "review") {
            val created =
                command(
                    f.employeeClient,
                    "${f.path}/employees/${f.employee}/attendance",
                    json.writeValueAsString(
                        mapOf(
                            "id" to id,
                            "deviceId" to device,
                            "workDate" to date,
                            "capturedAt" to clock.instant(),
                            "kind" to "CLOCK_IN",
                            "offline" to true,
                        )
                    ),
                    f.employeeCsrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, created.statusCode(), created.body())
        }
        var expire = false
        var advanced = false
        fun advance() {
            if (expire && !advanced) {
                clock.set(clock.instant().plusSeconds(2))
                advanced = true
            }
        }
        val observedAttendance =
            object : AttendanceRepository by attendance {
                override fun findWindow(companyId: UUID, id: UUID) =
                    attendance.findWindow(companyId, id).also {
                        if (replay && mode == "window") advance()
                    }
            }
        val observedCorrections =
            object : AttendanceCorrectionRepository by corrections {
                override fun latest(
                    companyId: UUID,
                    employeeId: UUID,
                    from: LocalDate,
                    until: LocalDate,
                ) =
                    corrections.latest(companyId, employeeId, from, until).also {
                        if (mode == "days") advance()
                    }

                override fun history(
                    companyId: UUID,
                    employeeId: UUID,
                    date: LocalDate,
                    after: Long?,
                    limit: Int,
                ) =
                    corrections.history(companyId, employeeId, date, after, limit).also {
                        if (mode == "history") advance()
                    }
            }
        val observedPeople =
            object : PeopleRepository by people {
                override fun find(companyId: UUID, id: UUID, asOf: LocalDate): Result<Employee?> =
                    people.find(companyId, id, asOf).also {
                        if (replay && mode in setOf("capture", "correction")) advance()
                    }

                override fun findAtInstant(
                    companyId: UUID,
                    id: UUID,
                    at: Instant,
                ): Result<Employee?> =
                    people.findAtInstant(companyId, id, at).also {
                        if (replay && mode == "review") advance()
                    }
            }
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also { if (!replay) advance() }
            }
        val observedOperations =
            object : OperationRepository by operations {
                override fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt) =
                    operations.record(actor, key, receipt).also {
                        if (!replay && mode == "window") advance()
                    }
            }
        fun snapshot() =
            listOf(
                    "attendance_capture_windows",
                    "attendance_events",
                    "attendance_reviews",
                    "attendance_day_versions",
                    "attendance_corrections",
                    "work_periods",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                    "mobile_sync_changes",
                )
                .associateWith { table ->
                    database()
                        .queryForList(
                            "select to_jsonb(t)::text from $table t where company_id=? order by 1",
                            String::class.java,
                            f.company,
                        )
                }
        fun invoke(current: Actor): Result<*> =
            when (mode) {
                "window" ->
                    IssueAttendanceCaptureWindow(
                            observedAttendance,
                            observedPeople,
                            companies,
                            observedOperations,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, operation, f.employee, device)
                "capture" ->
                    RecordAttendance(
                            periods,
                            observedAttendance,
                            observedCorrections,
                            schedules,
                            observedPeople,
                            companies,
                            observedOperations,
                            observedJournal,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, operation, capture)
                "review" ->
                    ReviewAttendance(
                            periods,
                            observedAttendance,
                            observedCorrections,
                            observedPeople,
                            observedOperations,
                            observedJournal,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(
                            current,
                            operation,
                            id,
                            0,
                            AttendanceReviewDecision.REJECT,
                            "Independent review",
                        )
                "correction" ->
                    CorrectAttendance(
                            periods,
                            observedAttendance,
                            observedCorrections,
                            schedules,
                            observedPeople,
                            companies,
                            observedOperations,
                            observedJournal,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(
                            current,
                            operation,
                            f.employee,
                            date,
                            null,
                            null,
                            0,
                            null,
                            "Verified absence",
                        )
                "days" ->
                    GetEmployeeAttendance(
                            observedAttendance,
                            observedCorrections,
                            observedPeople,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, f.employee, date, date)
                else ->
                    GetAttendanceCorrectionHistory(
                            observedCorrections,
                            observedPeople,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, f.employee, date, null, 20)
            }
        if (replay) {
            val original = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
            assertTrue(original is Result.Success, original.toString())
        }
        val before = snapshot()
        expire = completion
        val barrier = AccountLockProbe.Barrier(account)
        if (!completion) accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    if (!completion) {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        barrier.release.countDown()
                    }
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        expire = false
        if (completion)
            assertTrue(advanced, "The selected acquisition/completion boundary was not reached")
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code, result.toString())
        assertEquals(before, snapshot())
        assertEquals(
            if (replay) 1 else 0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    operation,
                ),
        )
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(renewed)
        assertTrue(accepted is Result.Success, "$mode $accepted")
        val expired = renewed.copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge))
        assertEquals("mfa_required", (invoke(expired) as Result.Failed).failure.code)
        if (mode in setOf("window", "capture", "review", "correction")) {
            val replay = invoke(renewed) as Result.Success
            val expected = (accepted as Result.Success).value
            assertEquals(
                if (expected is MutationReceipt) expected.copy(replayed = true) else expected,
                replay.value,
            )
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        operation,
                    ),
            )
        }
    }
}
