package dev.fajar.hris.identity.delivery.responses

data class MfaVerificationResponse(val verifiedAt: String, val recoveryCodes: List<String>) {
    override fun toString(): String = "MfaVerificationResponse(<redacted>)"
}
