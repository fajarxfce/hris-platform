package dev.fajar.hris.approvals.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import java.time.Instant
import java.util.UUID

fun selectApprovalTemplate(
    templates: List<ApprovalTemplate>,
    context: ApprovalContext,
): Result<ApprovalTemplate> {
    if (templates.size > 200)
        return Result.Failed(Failure(FailureKind.CONFLICT, "approval_policy_capacity"))
    val matches =
        templates
            .filter {
                it.active &&
                    it.kind == context.kind &&
                    !it.effectiveFrom.isAfter(context.referenceDate) &&
                    (it.category == null || it.category == context.category) &&
                    context.amount >= it.minimumAmount
            }
            .sortedWith(
                compareByDescending<ApprovalTemplate> { it.category != null }
                    .thenByDescending { it.minimumAmount }
                    .thenByDescending { it.effectiveFrom }
            )
    val first =
        matches.firstOrNull()
            ?: return Result.Failed(Failure(FailureKind.VALIDATION, "approval_policy_missing"))
    if (
        matches.drop(1).any {
            it.category == first.category &&
                it.minimumAmount.compareTo(first.minimumAmount) == 0 &&
                it.effectiveFrom == first.effectiveFrom
        }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "ambiguous_approval_policy"))
    return Result.Success(first)
}

fun snapshotApproval(
    template: ApprovalTemplate,
    context: ApprovalContext,
    members: List<MemberAccount>,
): Result<ApprovalRequest> {
    if (members.size > 200)
        return Result.Failed(Failure(FailureKind.VALIDATION, "approval_group_too_large"))
    val eligible =
        members.filter {
            it.accountActive &&
                it.membershipActive &&
                it.id != context.authorId &&
                it.id != context.requesterId &&
                it.permissions.any { p -> p in approvalPermissions(context.kind) }
        }
    val stages =
        template.stages.map { rule ->
            ApprovalStage(
                eligible
                    .filter { member ->
                        when (rule.assignment) {
                            AssignmentKind.MANAGER -> member.id == context.managerAccountId
                            AssignmentKind.NAMED -> member.id in rule.accountIds
                            AssignmentKind.PERMISSION -> rule.permission in member.permissions
                        }
                    }
                    .map { it.id }
                    .toSet()
            )
        }
    if (stages.isEmpty())
        return Result.Failed(Failure(FailureKind.VALIDATION, "approval_policy_empty"))
    return Result.Success(
        ApprovalRequest(
            context.id,
            context.kind,
            context.resourceId,
            context.authorId,
            context.requesterId,
            template.id,
            template.revision,
            stages,
            0,
            if (stages.first().assignees.isEmpty()) ApprovalStatus.BLOCKED
            else ApprovalStatus.PENDING,
            0,
            context.submittedAt,
        )
    )
}

fun decideApproval(
    request: ApprovalRequest,
    actor: Actor,
    decision: ApprovalDecision,
    reason: String,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
): Result<ApprovalTransition> {
    if (delegations.size > 200)
        return Result.Failed(Failure(FailureKind.CONFLICT, "approval_delegation_capacity"))
    if (request.status != ApprovalStatus.PENDING)
        return Result.Failed(Failure(FailureKind.CONFLICT, "approval_not_pending"))
    if (actor.accountId == request.authorId || actor.accountId == request.requesterId)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
    if (actor.permissions.none { it in approvalPermissions(request.kind) })
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
    if (reason.length > 1000 || (decision == ApprovalDecision.REJECT && reason.isBlank()))
        return Result.Failed(Failure(FailureKind.VALIDATION, "decision_reason_required"))
    val assignees = request.stages[request.currentStep].assignees
    val decidingFor =
        if (actor.accountId in assignees) actor.accountId
        else
            delegations
                .firstOrNull {
                    it.active &&
                        it.kind == request.kind &&
                        it.toAccount == actor.accountId &&
                        it.fromAccount in assignees &&
                        !at.isBefore(it.validFrom) &&
                        at.isBefore(it.validUntil) &&
                        members.any { member ->
                            member.id == it.fromAccount &&
                                member.accountActive &&
                                member.membershipActive &&
                                member.permissions.any { permission ->
                                    permission in approvalPermissions(request.kind)
                                }
                        }
                }
                ?.fromAccount
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "not_assigned_approver"))
    val next = request.currentStep + 1
    val status =
        when {
            decision == ApprovalDecision.REJECT -> ApprovalStatus.REJECTED
            next == request.stages.size -> ApprovalStatus.APPROVED
            request.stages[next].assignees.isEmpty() -> ApprovalStatus.BLOCKED
            else -> ApprovalStatus.PENDING
        }
    return Result.Success(
        ApprovalTransition(
            status,
            if (status in setOf(ApprovalStatus.PENDING, ApprovalStatus.BLOCKED)) next
            else request.currentStep,
            decision,
            decidingFor,
        )
    )
}

fun approvalCandidateIds(template: ApprovalTemplate, context: ApprovalContext): Set<UUID> =
    template.stages.flatMap { it.accountIds }.toSet() + listOfNotNull(context.managerAccountId)

fun approvalCandidatePermissions(template: ApprovalTemplate): Set<String> =
    template.stages.mapNotNull { it.permission }.toSet()
