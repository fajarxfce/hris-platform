package dev.fajar.hris.leave.delivery.responses

data class LeaveBatchCountsResponse(
    val applied: Int,
    val unchanged: Int,
    val skipped: Int,
    val failed: Int,
    val completed: Int,
)
