package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.decideApproval
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.Instant
import java.util.UUID

fun canManageOvertime(actor: Actor, employee: Employee?, today: java.time.LocalDate): Boolean =
    "overtime.manage" in actor.permissions ||
        ("overtime.self.manage" in actor.permissions &&
            employee?.person?.accountId == actor.accountId &&
            employee.terms.isWorkingOn(today))

fun canReadOvertime(actor: Actor, employee: Employee?): Boolean =
    actor.permissions.any { it in setOf("overtime.read", "overtime.manage", "workforce.read") } ||
        (employee != null &&
            (("overtime.self.manage" in actor.permissions &&
                employee.person.accountId == actor.accountId) ||
                (actor.permissions.any {
                    it in setOf("overtime.team.read", "workforce.team.read")
                } &&
                    employee.managerAccountId == actor.accountId &&
                    employee.terms.status != EmploymentStatus.ENDED)))

fun independentOvertimeDecision(
    request: OvertimeRequest,
    actorId: UUID,
    decidingFor: UUID,
    currentBeneficiary: UUID?,
): Result<Unit> =
    if (
        setOf(actorId, decidingFor).any {
            it in
                setOfNotNull(
                    request.authorId,
                    request.submittedBy,
                    request.requesterAccountId,
                    currentBeneficiary,
                )
        }
    )
        Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
    else Result.Success(Unit)

fun overtimeActions(
    actor: Actor,
    request: OvertimeRequest,
    canManage: Boolean,
    mutablePeriod: Boolean,
    approval: ApprovalRequest?,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    currentBeneficiary: UUID?,
    now: Instant,
): Set<OvertimeAction> {
    if (!mutablePeriod) return emptySet()
    val result = mutableSetOf<OvertimeAction>()
    if (canManage && request.status in setOf(OvertimeStatus.PLANNED, OvertimeStatus.PENDING)) {
        result += OvertimeAction.WITHDRAW
        if (request.status == OvertimeStatus.PLANNED) result += OvertimeAction.SUBMIT_ACTUAL
    }
    if (request.status == OvertimeStatus.PENDING && approval != null) {
        val proposed =
            decideApproval(approval, actor, ApprovalDecision.APPROVE, "", delegations, members, now)
        if (
            proposed is Result.Success &&
                independentOvertimeDecision(
                    request,
                    actor.accountId,
                    proposed.value.decidingFor,
                    currentBeneficiary,
                ) is
                    Result.Success
        ) {
            result += OvertimeAction.APPROVE
            result += OvertimeAction.REJECT
        }
    }
    return result.toSet()
}
