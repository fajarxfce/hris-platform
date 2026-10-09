package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeavePolicySnapshot.toResponse(): LeavePolicySnapshotResponse =
    LeavePolicySnapshotResponse(
        typeId,
        code,
        revision,
        policy.name,
        policy.paid,
        policy.allowPartialDays,
        policy.minServiceMonths,
        policy.allowedContracts.map { it.name }.toSet(),
        policy.maxRequestDays,
        policy.attachmentRequired,
    )

fun LeaveDay.toResponse(): LeaveDayResponse =
    LeaveDayResponse(
        workDate,
        portion.name,
        portion.halfDays.toLeaveDays(),
        startsAt,
        endsAt,
        plannedMinutes,
        chargedMinutes,
        shiftId,
        shiftRevision,
        scheduleOrigin.name,
        scheduleRevision,
    )

fun ApprovalRequest.toLeaveWorkflow(): LeaveWorkflowResponse =
    LeaveWorkflowResponse(
        id,
        authorId,
        requesterId,
        status.name,
        currentStep,
        stages.map { it.assignees },
        version,
    )

fun LeaveRequestSummary.toResponse(): LeaveRequestSummaryResponse =
    LeaveRequestSummaryResponse(
        id,
        employeeId,
        employeeNumber,
        employeeName,
        typeCode,
        typeName,
        from,
        until,
        halfDays.toLeaveDays(),
        status.name,
        submittedAt,
        version,
    )

fun LeaveRequestDetails.toResponse(): LeaveRequestResponse =
    LeaveRequestResponse(
        request.id,
        request.employeeId,
        request.employeeNumber,
        request.employeeName,
        request.ownerAccountId,
        request.authorId,
        request.submittedAt,
        request.policy.toResponse(),
        request.days.map { it.toResponse() },
        request.days.sumOf { it.portion.halfDays }.toLeaveDays(),
        request.reason,
        request.status.name,
        request.version,
        approval.toLeaveWorkflow(),
        cancellation?.toLeaveWorkflow(),
        Page(
            history.items.map {
                LeaveRequestChangeResponse(
                    it.version,
                    it.kind.name,
                    it.status.name,
                    it.cancellationApprovalId,
                    it.actorId,
                    it.recordedAt,
                    it.reason,
                )
            },
            history.nextCursor,
        ),
        availableActions.map { it.name }.toSet(),
        request.attachments.map {
            LeaveAttachmentResponse(
                it.documentId,
                it.revisionId,
                it.fileName,
                it.mediaType,
                it.size,
                it.sha256,
            )
        },
    )
