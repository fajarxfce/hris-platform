package dev.fajar.hris.payroll.domain.entities

import java.time.*
import java.util.UUID

data class PayrollInput(
    val id: UUID,
    val employeeId: UUID,
    val earningsMonth: YearMonth,
    val version: Long,
    val workJobId: UUID,
    val workPeriodVersion: Long,
    val employmentVersion: Long,
    val terms: PayrollInputTerms,
    val status: PayrollInputStatus,
    val preparedBy: UUID,
    val verifiedBy: UUID?,
    val recordedAt: Instant,
    val reason: String,
)
