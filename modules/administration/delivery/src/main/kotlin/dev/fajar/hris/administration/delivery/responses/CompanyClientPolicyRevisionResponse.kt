package dev.fajar.hris.administration.delivery.responses

import dev.fajar.hris.administration.domain.entities.CompanyModule
import java.time.Instant
import java.util.UUID

data class CompanyClientPolicyRevisionResponse(
    val version: Long,
    val activateAt: Instant,
    val disabledModules: List<CompanyModule>,
    val minimumBuilds: MinimumClientBuildsResponse,
    val maintenance: MaintenanceWindowResponse?,
    val recordedAt: Instant,
    val actorId: UUID,
    val reason: String,
)
