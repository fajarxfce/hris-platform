package dev.fajar.hris.leave.delivery.requests

import java.util.UUID

data class LeaveYearCloseRequest(
    val id: UUID,
    val expectedPolicyVersion: Long,
    val expectedBalanceVersion: Long,
    val expectedDestinationVersion: Long? = null,
    val reason: String,
)
