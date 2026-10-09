package dev.fajar.hris.administration.domain.entities

import java.time.Instant

data class SaveCompanyClientPolicyCommand(
    val expectedVersion: Long?,
    val activateAt: Instant?,
    val policy: ClientPolicy,
    val reason: String,
)
