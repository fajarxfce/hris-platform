package dev.fajar.hris.approvals.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.*

fun validateApprovalTemplate(change: TemplateChange): Result<Unit> {
    if (
        change.name.isBlank() ||
            change.name.length > 200 ||
            change.reason.isBlank() ||
            change.reason.length > 1000 ||
            (change.expectedVersion ?: 0) < 0 ||
            (change.category?.length ?: 0) > 80 ||
            change.minimumAmount.signum() < 0 ||
            change.minimumAmount.scale() !in 0..2 ||
            change.minimumAmount.precision() > 18 ||
            change.stages.size !in 1..8
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_approval_template"))
    for (stage in change.stages) {
        val valid =
            when (stage.assignment) {
                AssignmentKind.MANAGER -> stage.accountIds.isEmpty() && stage.permission == null
                AssignmentKind.NAMED -> stage.accountIds.size in 1..25 && stage.permission == null
                AssignmentKind.PERMISSION ->
                    stage.accountIds.isEmpty() &&
                        stage.permission in approvalPermissions(change.kind)
            }
        if (!valid) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_approval_stage"))
    }
    return Result.Success(Unit)
}
