package dev.fajar.hris.administration.delivery.responses

import dev.fajar.hris.administration.domain.entities.CompanyModule
import java.time.Instant

data class ClientPolicyResponse(
    val schemaVersion: Int,
    val version: Long?,
    val enabledModules: List<CompanyModule>,
    val minimumBuilds: MinimumClientBuildsResponse,
    val maintenance: MaintenanceWindowResponse?,
    val maintenanceActive: Boolean,
    val serverTime: Instant,
    val validUntil: Instant,
)
