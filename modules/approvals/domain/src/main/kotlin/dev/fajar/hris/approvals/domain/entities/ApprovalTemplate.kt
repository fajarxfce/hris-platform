package dev.fajar.hris.approvals.domain.entities

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ApprovalTemplate(
    val id: UUID,
    val name: String,
    val kind: ApprovalKind,
    val active: Boolean,
    val version: Long,
    val revision: Long,
    val effectiveFrom: LocalDate,
    val category: String?,
    val minimumAmount: BigDecimal,
    val stages: List<StageRule>,
)
