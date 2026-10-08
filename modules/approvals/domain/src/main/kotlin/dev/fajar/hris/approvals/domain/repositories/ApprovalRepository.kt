package dev.fajar.hris.approvals.domain.repositories

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface ApprovalRepository {
    fun findDelegation(companyId: UUID, id: UUID): Result<Delegation?>

    fun findTemplate(companyId: UUID, id: UUID): Result<ApprovalTemplate?>

    fun templates(
        companyId: UUID,
        kind: ApprovalKind,
        asOf: LocalDate,
    ): Result<List<ApprovalTemplate>>

    fun saveTemplate(actor: Actor, change: TemplateChange): Result<MutationReceipt>

    fun create(companyId: UUID, request: ApprovalRequest): Result<Unit>

    fun find(companyId: UUID, id: UUID): Result<ApprovalRequest?>

    fun inbox(
        companyId: UUID,
        accountId: UUID,
        includeBlocked: Boolean,
        at: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<ApprovalRequest>>

    fun delegations(companyId: UUID, accountId: UUID, at: Instant): Result<List<Delegation>>

    fun decide(
        actor: Actor,
        id: UUID,
        version: Long,
        step: Int,
        transition: ApprovalTransition,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt>

    fun cancel(companyId: UUID, id: UUID, version: Long): Result<MutationReceipt>

    fun reassign(
        actor: Actor,
        request: ApprovalRequest,
        assignees: Set<UUID>,
        reason: String,
    ): Result<MutationReceipt>

    fun saveDelegation(
        companyId: UUID,
        delegation: Delegation,
        expectedVersion: Long?,
    ): Result<MutationReceipt>
}
