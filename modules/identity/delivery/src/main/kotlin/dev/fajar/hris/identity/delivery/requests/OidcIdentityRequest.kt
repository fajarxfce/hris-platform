package dev.fajar.hris.identity.delivery.requests

data class OidcIdentityRequest(
    val issuer: String,
    val subject: String,
    val active: Boolean = true,
    val expectedVersion: Long? = null,
    val reason: String,
)
