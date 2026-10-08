package dev.fajar.hris.approvals.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class TemplateResponse(
    val id: UUID,
    val name: String,
    val kind: String,
    val active: Boolean,
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: LocalDate,
    val category: String?,
    val minimumAmount: String,
    val stages: List<StageRuleResponse>,
)
