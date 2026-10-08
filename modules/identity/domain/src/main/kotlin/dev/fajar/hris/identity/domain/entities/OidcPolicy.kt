package dev.fajar.hris.identity.domain.entities

data class OidcPolicy(val enabled: Boolean = false, val issuer: String = "")
