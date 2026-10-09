package dev.fajar.hris.workforce.delivery.requests

data class OvertimeActualRequest(
    val expectedVersion: Long,
    val actual: OvertimeIntervalRequest,
    val reason: String,
)
