package dev.fajar.hris.identity.delivery.requests
data class PasswordRecoveryRequest(val email: String) {
    override fun toString() = "PasswordRecoveryRequest([redacted])"
}
