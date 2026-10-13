package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.documents.domain.entities.DocumentRevisionStatus
import java.util.UUID

data class ExpenseDraftReceiptReference(
    val revisionId: UUID,
    val documentId: UUID? = null,
    val fileName: String? = null,
    val mediaType: String? = null,
    val size: Long? = null,
    val status: DocumentRevisionStatus? = null,
)
