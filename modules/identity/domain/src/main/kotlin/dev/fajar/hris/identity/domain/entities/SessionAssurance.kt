package dev.fajar.hris.identity.domain.entities

import java.time.Instant

data class SessionAssurance(
    val required: Boolean,
    val verified: Boolean,
    val setupAvailable: Boolean,
    val validUntil: Instant?,
    val recentUntil: Instant?,
)
