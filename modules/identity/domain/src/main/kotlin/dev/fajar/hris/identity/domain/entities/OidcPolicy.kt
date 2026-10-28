package dev.fajar.hris.identity.domain.entities

data class OidcPolicy(
    val enabled: Boolean = false,
    val issuer: String = "",
    val additionalIssuers: Set<String> = emptySet(),
) {
    fun accepts(value: String) = (enabled && value == issuer) || value in additionalIssuers
}
