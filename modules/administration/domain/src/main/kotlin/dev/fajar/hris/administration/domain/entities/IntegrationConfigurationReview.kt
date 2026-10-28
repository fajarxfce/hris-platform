package dev.fajar.hris.administration.domain.entities

import java.time.Instant

data class IntegrationConfigurationReview(
    val observedAt: Instant,
    val integrations: List<IntegrationConfiguration>,
)
