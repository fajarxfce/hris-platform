package dev.fajar.hris.workforce.delivery.responses

import dev.fajar.hris.core.domain.Page

data class OvertimeDetailsResponse(
    val request: OvertimeResponse,
    val approval: OvertimeWorkflowResponse?,
    val history: Page<OvertimeChangeResponse>,
    val actions: Set<String>,
)
