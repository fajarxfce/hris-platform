package dev.fajar.hris.documents.delivery.requests

import dev.fajar.hris.documents.domain.entities.DocumentClassification
import java.util.UUID

data class SaveDocumentRetentionPolicyRequest(
    val policyId: UUID,
    val classification: DocumentClassification,
    val expectedVersion: Long?,
    val retentionDays: Int?,
    val reason: String,
)
