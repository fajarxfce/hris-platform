package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.OvertimeInterval
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired

/** Database/HTTP setup stays on the fixture policy; direct readers and commands enforce MFA. */
abstract class OvertimeAssuranceApiFixture : OvertimeApiFixture() {
    @Autowired private lateinit var overtime: OvertimeRepository
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    protected val security = IdentitySecurityPolicy()
    protected val trackedTables =
        listOf(
            "overtime_requests",
            "overtime_changes",
            "approval_requests",
            "approval_decisions",
            "operation_receipts",
            "audit_entries",
            "outbox_events",
            "mobile_sync_changes",
        )

    protected data class Scenario(
        val fixture: Fixture,
        val action: String,
        val id: UUID,
        val operation: UUID,
        val actor: Actor,
    )

    protected fun prepare(action: String): Scenario {
        val admin = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Overtime assurance fixture',password_hash from accounts where email='admin@example.test'",
                admin,
                "$admin@example.test",
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) select ?,permission from platform_permissions where account_id=(select id from accounts where email='admin@example.test')",
                admin,
            )
        val f = overtimeFixture("$admin@example.test")
        val id = UUID.randomUUID()
        if (action != "plan") overtimeBody(plan(f, id))
        if (action in setOf("decide", "withdraw", "details")) overtimeBody(actual(f, id))
        val self = action in setOf("plan", "actual", "withdraw")
        val account = if (self) f.employeeAccount else f.actor.accountId
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        return Scenario(
            f,
            action,
            id,
            UUID.randomUUID(),
            f.actor.copy(
                accountId = account,
                permissions = if (self) setOf("overtime.self.manage") else f.actor.permissions,
                mfaVerifiedAt = clock.instant(),
            ),
        )
    }

    protected fun invoke(scenario: Scenario, actor: Actor = scenario.actor): Result<*> {
        val f = scenario.fixture
        return when (scenario.action) {
            "plan" ->
                PlanOvertimeRequest(
                        overtime,
                        periods,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        schedules,
                        security,
                    )
                    .execute(
                        actor,
                        scenario.operation,
                        scenario.id,
                        f.employee,
                        0,
                        LocalDate.parse("2026-09-01"),
                        OvertimeInterval(
                            Instant.parse("2026-09-01T01:00:00Z"),
                            Instant.parse("2026-09-01T04:00:00Z"),
                            0,
                        ),
                        "Inventory count",
                    )
            "actual" ->
                SubmitOvertimeActual(
                        overtime,
                        periods,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        approvals,
                        security,
                    )
                    .execute(
                        actor,
                        scenario.operation,
                        scenario.id,
                        0,
                        OvertimeInterval(
                            Instant.parse("2026-09-01T01:15:00Z"),
                            Instant.parse("2026-09-01T03:45:00Z"),
                            15,
                        ),
                        "Work completed",
                    )
            "withdraw" ->
                WithdrawOvertimeRequest(
                        overtime,
                        periods,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        approvals,
                        security,
                    )
                    .execute(actor, scenario.operation, scenario.id, 1, "Request withdrawn")
            "decide" ->
                DecideOvertimeRequest(
                        overtime,
                        periods,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        approvals,
                        security,
                    )
                    .execute(
                        actor,
                        scenario.operation,
                        scenario.id,
                        1,
                        ApprovalDecision.APPROVE,
                        "Work verified",
                    )
            "details" ->
                GetOvertimeRequest(
                        overtime,
                        periods,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        approvals,
                        security,
                    )
                    .execute(actor, scenario.id, null, 50)
            "list" ->
                ListOvertimeRequests(
                        overtime,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        null,
                        LocalDate.parse("2026-09-01"),
                        LocalDate.parse("2026-09-30"),
                        null,
                        null,
                        50,
                    )
            else -> error("Unknown overtime fixture action")
        }
    }
}
