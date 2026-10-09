package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.*

fun OvertimeInterval.toResponse(): OvertimeIntervalResponse =
    OvertimeIntervalResponse(startsAt, endsAt, breakMinutes, workedMinutes)

fun OvertimeRequest.toResponse(): OvertimeResponse =
    OvertimeResponse(
        id,
        employeeId,
        employeeNumber,
        employeeName,
        requesterAccountId,
        authorId,
        createdAt,
        workDate,
        timezone,
        schedule.toResponse(),
        requested.toResponse(),
        reason,
        status.name,
        actual?.toResponse(),
        submittedBy,
        submittedAt,
        approvalId,
        approvedMinutes,
        decidedAt,
        version,
    )

fun OvertimeChange.toResponse(): OvertimeChangeResponse =
    OvertimeChangeResponse(
        revision,
        kind.name,
        status.name,
        actual?.toResponse(),
        approvedMinutes,
        approvalId,
        actorId,
        recordedAt,
        reason,
    )

fun OvertimeRequestDetails.toResponse(): OvertimeDetailsResponse =
    OvertimeDetailsResponse(
        request.toResponse(),
        approval?.let {
            OvertimeWorkflowResponse(
                it.id,
                it.status.name,
                it.currentStep,
                it.stages.map { stage -> stage.assignees },
                it.version,
            )
        },
        Page(history.items.map { it.toResponse() }, history.nextCursor),
        actions.map { it.name }.toSet(),
    )

fun ApprovedOvertime.toResponse(): ApprovedOvertimeResponse =
    ApprovedOvertimeResponse(
        requestId,
        revision,
        actual.toResponse(),
        approvedMinutes,
        schedule.toResponse(),
    )
