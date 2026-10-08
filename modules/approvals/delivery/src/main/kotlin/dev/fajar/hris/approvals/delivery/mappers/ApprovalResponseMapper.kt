package dev.fajar.hris.approvals.delivery.mappers

import dev.fajar.hris.approvals.delivery.responses.*
import dev.fajar.hris.approvals.domain.entities.*

fun ApprovalTemplate.toResponse(): TemplateResponse =
    TemplateResponse(
        id,
        name,
        kind.name,
        active,
        version,
        revision,
        effectiveFrom,
        category,
        minimumAmount.toPlainString(),
        stages.map { StageRuleResponse(it.assignment.name, it.accountIds, it.permission) },
    )

fun ApprovalRequest.toResponse(): ApprovalResponse =
    ApprovalResponse(
        id,
        kind.name,
        resourceId,
        authorId,
        requesterId,
        templateId,
        templateRevision,
        stages.map { ApprovalStageResponse(it.assignees) },
        currentStep,
        status.name,
        version,
        submittedAt,
    )

fun Delegation.toResponse(): DelegationResponse =
    DelegationResponse(
        id,
        kind.name,
        fromAccount,
        toAccount,
        validFrom,
        validUntil,
        active,
        version,
    )
