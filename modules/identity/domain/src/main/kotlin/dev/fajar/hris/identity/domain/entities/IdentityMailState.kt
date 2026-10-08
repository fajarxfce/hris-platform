package dev.fajar.hris.identity.domain.entities

enum class IdentityMailState {
    PENDING,
    LEASED,
    SENT,
    FAILED,
    SUPERSEDED,
}
