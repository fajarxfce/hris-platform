package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollFinalization(
    val id: UUID,
    val runId: UUID,
    val reviewId: UUID,
    val runVersion: Long,
    val reviewVersion: Long,
    val approvalVersion: Long,
    val number: Int,
    val jobId: UUID,
    val actorId: UUID,
    val requestedAt: Instant,
    val reason: String,
    val companyCode: String,
    val companyName: String,
    val publishedAt: Instant? = null,
)
