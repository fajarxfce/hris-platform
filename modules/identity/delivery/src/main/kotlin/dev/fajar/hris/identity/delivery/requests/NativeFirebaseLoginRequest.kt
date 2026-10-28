package dev.fajar.hris.identity.delivery.requests

data class NativeFirebaseLoginRequest(val idToken: String, val deviceName: String) {
    override fun toString() = "NativeFirebaseLoginRequest(<redacted>)"
}
