package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollRunResponse(
    val id: UUID,
    val periodId: UUID,
    val number: Int,
    val earningsMonth: String,
    val taxMonth: String,
    val incomeDueDate: LocalDate,
    val plannedPaymentDate: LocalDate,
    val timezone: String,
    val workJobId: UUID,
    val workPeriodVersion: Long,
    val policyRevision: Long,
    val totalEmployees: Int,
    val actorId: UUID,
    val reviewReference: String,
    val reason: String,
    val createdAt: Instant,
    val jobId: UUID,
    val status: String,
    val version: Long,
    val processed: Int,
    val succeeded: Int,
    val failed: Int,
)
