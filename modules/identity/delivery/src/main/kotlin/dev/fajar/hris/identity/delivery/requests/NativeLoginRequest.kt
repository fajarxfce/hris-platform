package dev.fajar.hris.identity.delivery.requests

data class NativeLoginRequest(val email: String, val password: String, val deviceName: String) {
    override fun toString(): String = "NativeLoginRequest(<redacted>)"
}
