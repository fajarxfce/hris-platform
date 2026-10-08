package dev.fajar.hris.documents.domain.entities

enum class DocumentInventoryDisposition {
    RETAINED,
    UNKNOWN,
    ANOMALOUS,
    QUEUED,
    RECOVERY_EXHAUSTED,
    SCHEDULE,
}
