package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID

data class PayrollRun(
    val id: UUID,
    val periodId: UUID,
    val number: Int,
    val earningsMonth: YearMonth,
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
    val status: PayrollRunStatus,
    val version: Long,
    val processed: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
)
