package dev.fajar.hris.identity.delivery.requests

data class MfaVerificationRequest(val code: String, val recovery: Boolean = false) {
    override fun toString(): String = "MfaVerificationRequest(<redacted>)"
}
