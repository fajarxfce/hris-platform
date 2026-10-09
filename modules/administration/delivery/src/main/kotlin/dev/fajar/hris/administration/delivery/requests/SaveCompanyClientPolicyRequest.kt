package dev.fajar.hris.administration.delivery.requests

import dev.fajar.hris.administration.domain.entities.CompanyModule
import java.time.Instant

data class SaveCompanyClientPolicyRequest(
    val expectedVersion: Long?,
    val activateAt: Instant?,
    val disabledModules: Set<CompanyModule>,
    val minimumBuilds: MinimumClientBuildsRequest,
    val maintenance: MaintenanceWindowRequest?,
    val reason: String,
)
