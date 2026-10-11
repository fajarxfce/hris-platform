package dev.fajar.hris.leave.delivery.requests

import java.util.UUID

data class LeaveSubmissionRequest(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val days: List<LeaveDayRequest>,
    val reason: String,
    val attachmentRevisionIds: List<UUID> = emptyList(),
    @field:io.swagger.v3.oas.annotations.media.Schema(
        pattern = "[0-9a-f]{64}",
        minLength = 64,
        maxLength = 64,
    )
    val previewRevision: String? = null,
)
