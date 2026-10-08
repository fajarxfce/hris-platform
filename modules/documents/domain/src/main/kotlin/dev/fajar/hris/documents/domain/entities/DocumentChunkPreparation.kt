package dev.fajar.hris.documents.domain.entities

import dev.fajar.hris.core.domain.MutationReceipt

sealed interface DocumentChunkPreparation {
    data class Replayed(val receipt: MutationReceipt) : DocumentChunkPreparation

    data class Reserved(val lease: DocumentUploadLease) : DocumentChunkPreparation
}
