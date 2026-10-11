package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.*
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
    fun expiryWhileWaitingCannotAuthorizeAttendanceOrReplay(mode: String) {
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
        fun invoke(current: Actor): Result<*> =
            when (mode) {
                "window" ->
                    IssueAttendanceCaptureWindow(
                            attendance,
                            people,
                            companies,
                            operations,
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
                            attendance,
                            corrections,
                            schedules,
                            people,
                            companies,
                            operations,
                            journal,
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
                            attendance,
                            corrections,
                            people,
                            operations,
                            journal,
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
                            attendance,
                            corrections,
                            schedules,
                            people,
                            companies,
                            operations,
                            journal,
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
                            attendance,
                            corrections,
                            people,
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
                            corrections,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, f.employee, date, null, 20)
            }
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertEquals(
            0,
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
