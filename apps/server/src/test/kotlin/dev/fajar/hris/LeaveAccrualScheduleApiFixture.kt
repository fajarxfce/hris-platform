package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.leave.domain.entities.ScheduleLeaveAccrualCommand
import dev.fajar.hris.leave.domain.usecases.*
import java.net.http.HttpResponse
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(LeaveAccrualScheduleProbeConfiguration::class)
abstract class LeaveAccrualScheduleApiFixture : LeaveBatchApiFixture() {
    @Autowired protected lateinit var scheduleAccrual: ScheduleLeaveAccrual
    @Autowired protected lateinit var reviewSchedule: ReviewLeaveAccrualSchedule
    @Autowired protected lateinit var getSchedule: GetLeaveAccrualSchedule
    @Autowired protected lateinit var listSchedules: ListLeaveAccrualSchedules
    @Autowired protected lateinit var scheduleProbe: LeaveAccrualScheduleProbe
    protected val scheduleSecurity = IdentitySecurityPolicy(enforceMfa = true)

    @AfterEach
    fun clearScheduleProbe() {
        scheduleProbe.clear()
    }

    protected fun scheduledFixture(
        frequency: String = "MONTHLY",
        at: Instant = Instant.parse("2026-10-01T15:00:00Z"),
    ): LeaveFixture =
        accountingFixture(at = at, frequency = frequency).also { trackBatchCompany(it) }

    protected fun scheduleCommand(
        f: LeaveFixture,
        first: String = "2026-10",
        periods: Int = 3,
        id: UUID = UUID.randomUUID(),
        version: Long = 1,
        employees: Set<UUID> = setOf(f.employee),
    ) =
        ScheduleLeaveAccrualCommand(
            id,
            f.type,
            YearMonth.parse(first),
            periods,
            version,
            employees,
            "Reviewed entitlement schedule",
        )

    protected fun submitSchedule(
        f: LeaveFixture,
        input: ScheduleLeaveAccrualCommand = scheduleCommand(f),
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/leave/accrual-schedules",
            json.writeValueAsString(input),
            f.adminCsrf,
            key,
        )

    protected fun scheduleView(f: LeaveFixture, id: UUID): JsonNode =
        accountingBody(get(f.admin, "/api/v1/companies/${f.company}/leave/accrual-schedules/$id"))

    protected fun scheduleRows(f: LeaveFixture): Map<String, Int> =
        listOf(
                "leave_accrual_schedules",
                "leave_accrual_schedule_entries",
                "leave_batches",
                "leave_batch_targets",
                "leave_batch_attempts",
                "background_jobs",
                "audit_entries",
                "outbox_events",
                "operation_receipts",
                "leave_accounts",
                "leave_ledger",
                "leave_accrual_postings",
            )
            .associateWith { accountingRows(f, it) }

    protected fun scheduleOperator(f: LeaveFixture): Actor {
        val permissions = setOf("company.read", "leave.read", "leave.accrual.post")
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?) on conflict do nothing",
                f.company,
                f.managerAccount,
                permission,
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.managerAccount,
            )
        return Actor(
            f.managerAccount,
            f.company,
            permissions,
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant().minus(scheduleSecurity.maximumMfaAge).plusSeconds(1),
        )
    }
}
