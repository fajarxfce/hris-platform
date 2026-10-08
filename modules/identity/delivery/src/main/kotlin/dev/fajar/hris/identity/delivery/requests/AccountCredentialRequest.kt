package dev.fajar.hris.identity.delivery.requests
data class AccountCredentialRequest(val token: String, val password: String) {
    override fun toString() = "AccountCredentialRequest([redacted])"
}
