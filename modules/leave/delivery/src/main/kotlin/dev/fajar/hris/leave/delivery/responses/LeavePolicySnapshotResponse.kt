package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeavePolicySnapshotResponse(
    val typeId: UUID,
    val code: String,
    val revision: Long,
    val name: String,
    val paid: Boolean,
    val allowPartialDays: Boolean,
    val minServiceMonths: Int,
    val allowedContracts: Set<String>,
    val maxRequestDays: Int,
)
