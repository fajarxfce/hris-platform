package dev.fajar.hris.approvals.data.repositories

import dev.fajar.hris.approvals.data.datasources.*
import dev.fajar.hris.approvals.data.mappers.*
import dev.fajar.hris.approvals.data.models.StageRuleData
import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredApprovalRepository(
    private val policies: ApprovalPolicyDataSource,
    private val requests: ApprovalRequestDataSource,
    private val delegationSource: DelegationDataSource,
    private val json: ObjectMapper,
) : ApprovalRepository {
    override fun findDelegation(companyId: UUID, id: UUID): Result<Delegation?> = safeDatabaseCall {
        delegationSource.find(companyId, id)?.toDelegation()
    }

    override fun templates(
        companyId: UUID,
        kind: ApprovalKind,
        asOf: LocalDate,
    ): Result<List<ApprovalTemplate>> = safeDatabaseCall {
        policies.list(companyId, kind.name, asOf).map { it.toTemplate(json) }
    }

    override fun findTemplate(companyId: UUID, id: UUID): Result<ApprovalTemplate?> =
        safeDatabaseCall {
            policies.find(companyId, id)?.toTemplate(json)
        }

    override fun saveTemplate(actor: Actor, change: TemplateChange): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        val expected = change.expectedVersion
        return safeDatabaseCall {
                val row =
                    ApprovalTemplatesRecord().also {
                        it.companyId = company
                        it.id = change.id
                        it.name = change.name
                        it.kind = change.kind.name
                        it.active = change.active
                        it.version = expected ?: 0
                    }
                if (expected == null) {
                    policies.insert(row)
                    0L
                } else policies.update(row, expected)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    policies.appendRevision(
                        ApprovalTemplateRevisionsRecord().also {
                            it.companyId = company
                            it.templateId = change.id
                            it.revision = version
                            it.effectiveFrom = change.effectiveFrom
                            it.category = change.category
                            it.minimumAmount = change.minimumAmount
                            it.stages =
                                JSONB.valueOf(
                                    json.writeValueAsString(
                                        change.stages.map { stage ->
                                            StageRuleData(
                                                stage.assignment.name,
                                                stage.accountIds,
                                                stage.permission,
                                            )
                                        }
                                    )
                                )
                            it.actorId = actor.accountId
                            it.reason = change.reason
                        }
                    )
                    MutationReceipt(change.id, version)
                }
            }
    }

    override fun create(companyId: UUID, request: ApprovalRequest): Result<Unit> =
        safeDatabaseCall {
            requests.insert(request.toRow(companyId, json))
        }

    override fun find(companyId: UUID, id: UUID): Result<ApprovalRequest?> = safeDatabaseCall {
        requests
            .find(companyId, id)
            ?.toRequest(json, requests.latestAssignments(companyId, setOf(id)))
    }

    override fun inbox(
        companyId: UUID,
        accountId: UUID,
        includeBlocked: Boolean,
        at: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<ApprovalRequest>> = safeDatabaseCall {
        val rows =
            requests.inbox(
                companyId,
                accountId,
                includeBlocked,
                at.atOffset(ZoneOffset.UTC),
                after,
                limit + 1,
            )
        val overrides =
            requests.latestAssignments(companyId, rows.map { it.id }.toSet()).groupBy {
                it.requestId
            }
        Page(
            rows.take(limit).map { it.toRequest(json, overrides[it.id].orEmpty()) },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun delegations(
        companyId: UUID,
        accountId: UUID,
        at: Instant,
    ): Result<List<Delegation>> = safeDatabaseCall {
        delegationSource.forAccount(companyId, accountId, at.atOffset(ZoneOffset.UTC)).map {
            it.toDelegation()
        }
    }

    override fun decide(
        actor: Actor,
        id: UUID,
        version: Long,
        step: Int,
        transition: ApprovalTransition,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                requests.update(
                    company,
                    id,
                    version,
                    transition.status.name,
                    transition.currentStep,
                )
            }
            .requireCurrentVersion()
            .flatMap { updated ->
                safeDatabaseCall {
                    requests.appendDecision(
                        ApprovalDecisionsRecord().also {
                            it.companyId = company
                            it.requestId = id
                            it.step = step
                            it.actorId = actor.accountId
                            it.decidingFor = transition.decidingFor
                            it.decision = transition.decision.name
                            it.reason = reason
                            it.decidedAt = at.atOffset(ZoneOffset.UTC)
                        }
                    )
                    MutationReceipt(id, updated)
                }
            }
    }

    override fun cancel(companyId: UUID, id: UUID, version: Long): Result<MutationReceipt> =
        safeDatabaseCall {
                requests.update(companyId, id, version, ApprovalStatus.CANCELLED.name, null)
            }
            .requireCurrentVersion()
            .map { MutationReceipt(id, it) }

    override fun reassign(
        actor: Actor,
        request: ApprovalRequest,
        assignees: Set<UUID>,
        reason: String,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                requests.update(
                    company,
                    request.id,
                    request.version,
                    ApprovalStatus.PENDING.name,
                    null,
                )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    requests.appendAssignment(
                        ApprovalAssignmentOverridesRecord().also {
                            it.companyId = company
                            it.requestId = request.id
                            it.step = request.currentStep
                            it.revision = version
                            it.assignees = assignees.toTypedArray()
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(request.id, version)
                }
            }
    }

    override fun saveDelegation(
        companyId: UUID,
        delegation: Delegation,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    delegationSource.insert(delegation.toRow(companyId))
                    0L
                } else delegationSource.update(delegation.toRow(companyId), expectedVersion)
            }
            .requireCurrentVersion()
            .map { MutationReceipt(delegation.id, it) }
}
