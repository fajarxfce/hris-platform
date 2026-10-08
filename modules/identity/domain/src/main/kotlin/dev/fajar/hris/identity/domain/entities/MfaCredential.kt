package dev.fajar.hris.identity.domain.entities

data class MfaCredential(
    val account: Account,
    val pending: PendingMfaEnrollment?,
    val lastCounter: Long?,
)
