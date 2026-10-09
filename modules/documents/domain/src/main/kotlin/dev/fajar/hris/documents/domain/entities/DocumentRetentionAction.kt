package dev.fajar.hris.documents.domain.entities

enum class DocumentRetentionAction {
    ARCHIVE,
    RESTORE,
    PLACE_HOLD,
    RELEASE_HOLD,
    RETIRE,
}
