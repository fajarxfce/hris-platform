package dev.fajar.hris.documents.domain.entities
enum class DocumentRevisionStatus {
    UPLOADING,
    VALIDATING,
    VALIDATION_FAILED,
    READY,
    REJECTED,
    CANCELLED,
    EXPIRED,
}
