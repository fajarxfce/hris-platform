package dev.fajar.hris.identity.domain.entities

import java.time.Instant

data class IdentityMailOutcome(
    val state: IdentityMailState,
    val availableAt: Instant,
    val failureCode: String?,
    val discardToken: Boolean,
)
