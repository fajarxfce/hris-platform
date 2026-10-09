package dev.fajar.hris.administration.domain.entities

import java.time.Instant

data class ClientPolicyStatus(
    val version: Long?,
    val policy: ClientPolicy,
    val maintenanceActive: Boolean,
    val evaluatedAt: Instant,
    val validUntil: Instant,
)
