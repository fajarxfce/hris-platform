package dev.fajar.hris.identity.delivery.responses

data class MfaEnrollmentResponse(
    val operationId: String,
    val secret: String,
    val otpauthUri: String,
    val expiresAt: String,
) {
    override fun toString(): String = "MfaEnrollmentResponse(<redacted>)"
}
