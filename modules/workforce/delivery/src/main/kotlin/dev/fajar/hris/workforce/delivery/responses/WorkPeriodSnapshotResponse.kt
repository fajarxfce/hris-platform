package dev.fajar.hris.workforce.delivery.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class WorkPeriodSnapshotResponse(
    val employeeId: UUID,
    val month: String,
    val days: List<ClosedWorkDayResponse>,
    val employee: WorkforceEmployeeResponse,
    val period: WorkPeriodResponse,
    @field:Schema(
        description =
            "This API supports retained attendance adjustments; permissions are checked independently."
    )
    val adjustmentsSupported: Boolean = true,
)
