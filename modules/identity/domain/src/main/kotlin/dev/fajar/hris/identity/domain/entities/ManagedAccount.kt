package dev.fajar.hris.identity.domain.entities

data class ManagedAccount(
    val account: Account,
    val invitationPending: Boolean,
    val platformPermissions: Set<String>,
)
