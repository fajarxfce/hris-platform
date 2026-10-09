package dev.fajar.hris.documents.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.DocumentRetentionDataSource
import dev.fajar.hris.documents.data.mappers.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentRetentionRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID

class StoredDocumentRetentionRepository(private val source: DocumentRetentionDataSource) :
    DocumentRetentionRepository {
    override fun policy(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.policy(companyId, id)?.toRetentionPolicy()
    }

    override fun policyFor(companyId: UUID, classification: DocumentClassification) =
        safeDatabaseCall {
            source.policyFor(companyId, classification.name)?.toRetentionPolicy()
        }

    override fun policies(companyId: UUID) = safeDatabaseCall {
        source.policies(companyId).map { it.toRetentionPolicy() }
    }

    override fun policyHistory(companyId: UUID, id: UUID, after: Long?, limit: Int) =
        safeDatabaseCall {
            require(limit in 1..200)
            val rows = source.policyHistory(companyId, id, after, limit + 1)
            val items = rows.take(limit).map { it.toRetentionPolicy() }
            Page(items, if (rows.size > limit) items.last().version.toString() else null)
        }

    override fun savePolicy(
        companyId: UUID,
        policy: DocumentRetentionPolicy,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insertPolicy(
                        DocumentRetentionPoliciesRecord().also {
                            it.companyId = companyId
                            it.id = policy.id
                            it.classification = policy.classification.name
                            it.version = 0
                        }
                    )
                    0L
                } else source.advancePolicy(companyId, policy.id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.appendPolicy(
                        DocumentRetentionPolicyRevisionsRecord().also {
                            it.companyId = companyId
                            it.policyId = policy.id
                            it.version = version
                            it.retentionDays = policy.retentionDays
                            it.actorId = policy.actorId
                            it.recordedAt = policy.recordedAt.atOffset(ZoneOffset.UTC)
                            it.reason = policy.reason
                        }
                    )
                    MutationReceipt(policy.id, version)
                }
            }

    override fun state(companyId: UUID, documentId: UUID) = safeDatabaseCall {
        source.state(companyId, documentId)?.toRetentionState()
    }

    override fun saveState(
        companyId: UUID,
        change: DocumentRetentionChange,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insertState(
                        DocumentRetentionStatesRecord().also {
                            it.companyId = companyId
                            it.documentId = change.state.documentId
                            it.version = 0
                        }
                    )
                    0L
                } else source.advanceState(companyId, change.state.documentId, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val state = change.state
                    source.appendChange(
                        DocumentRetentionChangesRecord().also {
                            it.companyId = companyId
                            it.documentId = state.documentId
                            it.version = version
                            it.kind = change.action.name
                            it.legalHold = state.legalHold
                            it.archivedAt = state.archive?.archivedAt?.atOffset(ZoneOffset.UTC)
                            it.policyId = state.archive?.policyId
                            it.policyVersion = state.archive?.policyVersion
                            it.retentionDays = state.archive?.retentionDays
                            it.eligibleAt = state.archive?.eligibleAt?.atOffset(ZoneOffset.UTC)
                            it.actorId = change.actorId
                            it.recordedAt = change.recordedAt.atOffset(ZoneOffset.UTC)
                            it.reason = change.reason
                        }
                    )
                    MutationReceipt(state.documentId, version)
                }
            }

    override fun history(companyId: UUID, documentId: UUID, after: Long?, limit: Int) =
        safeDatabaseCall {
            require(limit in 1..200)
            val rows = source.history(companyId, documentId, after, limit + 1)
            val items = rows.take(limit).map { it.toRetentionChange() }
            Page(
                items,
                if (rows.size > limit) requireNotNull(items.last().state.version).toString()
                else null,
            )
        }
}
