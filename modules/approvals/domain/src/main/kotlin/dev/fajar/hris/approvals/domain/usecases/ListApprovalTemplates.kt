package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import java.time.LocalDate

class ListApprovalTemplates(
    private val approvals: ApprovalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, kind: ApprovalKind, asOf: LocalDate): Result<List<ApprovalTemplate>> =
        actor.requirePermission("approvals.manage").flatMap {
            transactions.run(actor) {
                approvals.templates(requireNotNull(actor.companyId), kind, asOf)
            }
        }
}
