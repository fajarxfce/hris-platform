package dev.fajar.hris.identity.delivery.responses

data class NativeMfaResponse(
    val credentials: NativeTokensResponse,
    val recoveryCodes: List<String> = emptyList(),
) {
    override fun toString(): String = "NativeMfaResponse(<redacted>)"
}
