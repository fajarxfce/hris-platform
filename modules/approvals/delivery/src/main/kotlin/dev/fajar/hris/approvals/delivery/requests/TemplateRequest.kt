package dev.fajar.hris.approvals.delivery.requests

import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import java.time.LocalDate

data class TemplateRequest(
    val name: String,
    val kind: ApprovalKind,
    val active: Boolean = true,
    val expectedVersion: Long? = null,
    val effectiveFrom: LocalDate,
    val category: String? = null,
    val minimumAmount: String = "0",
    val stages: List<StageRuleRequest>,
    val reason: String,
)
