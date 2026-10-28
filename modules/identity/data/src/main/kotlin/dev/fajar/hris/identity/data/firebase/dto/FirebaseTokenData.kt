package dev.fajar.hris.identity.data.firebase.dto

data class FirebaseTokenData(
    val issuer: String,
    val subject: String,
    val audience: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val authenticatedAt: Long,
    val signInProvider: String,
    val tenantId: String?,
) {
    override fun toString() = "FirebaseTokenData(<redacted>)"
}
