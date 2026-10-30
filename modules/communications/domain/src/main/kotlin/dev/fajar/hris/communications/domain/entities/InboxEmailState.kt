package dev.fajar.hris.communications.domain.entities

enum class InboxEmailState {
    PENDING,
    LEASED,
    ACCEPTED,
    FAILED,
    SUPERSEDED,
}
