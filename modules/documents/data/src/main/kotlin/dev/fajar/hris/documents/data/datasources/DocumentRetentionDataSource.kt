package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface DocumentRetentionDataSource {
    fun policy(companyId: UUID, id: UUID): DocumentRetentionPolicyViewsRecord?

    fun policyFor(companyId: UUID, classification: String): DocumentRetentionPolicyViewsRecord?

    fun policies(companyId: UUID): List<DocumentRetentionPolicyViewsRecord>

    fun policyHistory(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<DocumentRetentionPolicyViewsRecord>

    fun insertPolicy(row: DocumentRetentionPoliciesRecord)

    fun advancePolicy(companyId: UUID, id: UUID, expectedVersion: Long): Long?

    fun appendPolicy(row: DocumentRetentionPolicyRevisionsRecord)

    fun state(companyId: UUID, documentId: UUID): DocumentRetentionStateViewsRecord?

    fun insertState(row: DocumentRetentionStatesRecord)

    fun advanceState(companyId: UUID, documentId: UUID, expectedVersion: Long): Long?

    fun appendChange(row: DocumentRetentionChangesRecord)

    fun history(
        companyId: UUID,
        documentId: UUID,
        after: Long?,
        limit: Int,
    ): List<DocumentRetentionStateViewsRecord>
}
