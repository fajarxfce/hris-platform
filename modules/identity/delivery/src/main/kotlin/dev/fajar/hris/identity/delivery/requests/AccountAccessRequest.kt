package dev.fajar.hris.identity.delivery.requests

data class AccountAccessRequest(
    val expectedVersion: Long,
    val active: Boolean,
    val platformPermissions: Set<String>,
    val reason: String,
)
