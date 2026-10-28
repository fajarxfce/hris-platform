package dev.fajar.hris.administration.delivery.responses

import java.time.Instant

data class IntegrationConfigurationResponse(
    val runtime: String,
    val observedAt: Instant,
    val integrations: List<IntegrationConfigurationEntryResponse>,
)

data class IntegrationConfigurationEntryResponse(val kind: String, val enabled: Boolean)
