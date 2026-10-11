package dev.fajar.hris.leave.delivery.requests

import io.swagger.v3.oas.annotations.media.ArraySchema
import java.util.UUID

data class LeavePreviewRequest(
    val employeeId: UUID,
    val typeId: UUID,
    @field:ArraySchema(minItems = 1, maxItems = 366) val days: List<LeaveDayRequest>,
)
