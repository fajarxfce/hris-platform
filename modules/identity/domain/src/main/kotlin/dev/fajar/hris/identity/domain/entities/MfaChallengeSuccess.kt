package dev.fajar.hris.identity.domain.entities

data class MfaChallengeSuccess(val proof: MfaProof, val recoveryCodes: List<String> = emptyList()) {
    override fun toString(): String = "MfaChallengeSuccess(<redacted>)"
}
