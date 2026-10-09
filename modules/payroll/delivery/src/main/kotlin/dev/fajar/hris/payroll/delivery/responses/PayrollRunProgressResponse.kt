package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollRunProgressResponse(
    val jobId: UUID,
    val status: String,
    val completedItems: Int,
    val totalItems: Int,
    val attempts: Int,
    val version: Long,
    val cancellationRequested: Boolean,
    val failureCode: String?,
)
