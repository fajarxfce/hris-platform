package dev.fajar.hris.collaboration.domain.entities

/** Deployment admission is separate from company availability and account permission. */
data class CollaborationServicePolicy(val enabled: Boolean = false)
