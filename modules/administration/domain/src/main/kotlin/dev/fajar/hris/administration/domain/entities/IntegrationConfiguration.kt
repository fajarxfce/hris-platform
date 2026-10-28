package dev.fajar.hris.administration.domain.entities

/** Local startup configuration; enabling an integration is not proof of provider delivery. */
data class IntegrationConfiguration(val kind: IntegrationKind, val enabled: Boolean)
