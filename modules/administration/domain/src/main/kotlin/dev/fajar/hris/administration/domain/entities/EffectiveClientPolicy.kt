package dev.fajar.hris.administration.domain.entities

import java.time.Instant

data class EffectiveClientPolicy(
    val revision: CompanyClientPolicyRevision?,
    val nextActivation: Instant?,
)
