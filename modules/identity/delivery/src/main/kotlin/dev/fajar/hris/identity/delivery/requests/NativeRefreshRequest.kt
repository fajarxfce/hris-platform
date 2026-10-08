package dev.fajar.hris.identity.delivery.requests

data class NativeRefreshRequest(val refreshToken: String) {
    override fun toString(): String = "NativeRefreshRequest(<redacted>)"
}
