package dev.fajar.hris.identity.delivery.responses

data class SessionAssuranceResponse(
    val required: Boolean,
    val verified: Boolean,
    val setupAvailable: Boolean,
    val validUntil: String?,
    val recentUntil: String?,
)
