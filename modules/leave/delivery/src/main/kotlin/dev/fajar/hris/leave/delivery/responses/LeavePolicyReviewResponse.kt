package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page

data class LeavePolicyReviewResponse(
    val current: LeaveTypeResponse,
    val history: Page<LeavePolicyRevisionResponse>,
)
