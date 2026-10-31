package dev.fajar.hris.collaboration.domain.entities

/** Public provider identity only; signing secrets and internal addresses stay in data. */
data class CollaborationMatrixPolicy(val enabled: Boolean, val serverName: String)
