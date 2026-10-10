package dev.fajar.hris.identity.delivery.responses

/** Account identity is not a grant of company access; /me reports live assurance and scope. */
data class NativeLoginResponse(
    val account: AccountResponse,
    val credentials: NativeTokensResponse,
) {
    override fun toString(): String = "NativeLoginResponse(<redacted>)"
}
