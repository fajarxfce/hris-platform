package dev.fajar.hris.approvals.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ApprovalContext(
    val id: UUID,
    val resourceId: UUID,
    val kind: ApprovalKind,
    val authorId: UUID,
    val requesterId: UUID?,
    val managerAccountId: UUID?,
    val referenceDate: LocalDate,
    val category: String?,
    val amount: BigDecimal,
    val submittedAt: Instant,
)
