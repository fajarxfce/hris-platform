package dev.fajar.hris.documents.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import java.util.UUID

interface DocumentRetentionRepository {
    fun policy(companyId: UUID, id: UUID): Result<DocumentRetentionPolicy?>

    fun policyFor(
        companyId: UUID,
        classification: DocumentClassification,
    ): Result<DocumentRetentionPolicy?>

    fun policies(companyId: UUID): Result<List<DocumentRetentionPolicy>>

    fun policyHistory(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<DocumentRetentionPolicy>>

    fun savePolicy(
        companyId: UUID,
        policy: DocumentRetentionPolicy,
        expectedVersion: Long?,
    ): Result<MutationReceipt>

    fun state(companyId: UUID, documentId: UUID): Result<DocumentRetentionState?>

    fun saveState(
        companyId: UUID,
        change: DocumentRetentionChange,
        expectedVersion: Long?,
    ): Result<MutationReceipt>

    fun history(
        companyId: UUID,
        documentId: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<DocumentRetentionChange>>
}
