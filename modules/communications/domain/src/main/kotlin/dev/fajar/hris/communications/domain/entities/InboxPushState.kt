package dev.fajar.hris.communications.domain.entities

enum class InboxPushState {
    PENDING,
    LEASED,
    COMPLETE,
    FAILED,
    SUPERSEDED,
}
