package dev.fajar.hris.identity.domain.entities

data class IssuedCredentialLink(val challenge: CredentialChallenge, val token: String) {
    override fun toString() = "IssuedCredentialLink(id=${challenge.id}, <redacted>)"
}
