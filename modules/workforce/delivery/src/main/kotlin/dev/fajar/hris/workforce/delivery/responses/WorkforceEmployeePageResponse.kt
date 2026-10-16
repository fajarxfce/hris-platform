package dev.fajar.hris.workforce.delivery.responses

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

data class WorkforceEmployeePageResponse(
    @field:ArraySchema(maxItems = 50) val items: List<WorkforceEmployeeResponse>,
    val nextCursor: String?,
    @field:Schema(
        description =
            "This API supports reviewed bulk roster editing; permissions are checked independently."
    )
    val bulkRosterSupported: Boolean = true,
)
