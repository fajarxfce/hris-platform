package dev.fajar.hris.workforce.domain.entities

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest
import dev.fajar.hris.core.domain.Page

data class OvertimeRequestDetails(
    val request: OvertimeRequest,
    val approval: ApprovalRequest?,
    val history: Page<OvertimeChange>,
    val actions: Set<OvertimeAction>,
)
