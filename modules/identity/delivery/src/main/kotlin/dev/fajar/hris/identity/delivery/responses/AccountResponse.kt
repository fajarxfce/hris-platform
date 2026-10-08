package dev.fajar.hris.identity.delivery.responses

data class AccountResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val mfaConfigured: Boolean,
)
