package dev.fajar.hris.administration.domain.entities

/** The configured head and evaluated policy share one guarded read and evaluation instant. */
data class CompanyClientPolicySettings(
    val latest: CompanyClientPolicyRevision?,
    val effective: ClientPolicyStatus,
)
