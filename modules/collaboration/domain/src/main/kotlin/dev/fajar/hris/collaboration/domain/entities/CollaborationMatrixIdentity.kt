package dev.fajar.hris.collaboration.domain.entities

/** Public protocol identity, never a credential or a substitute for HRIS admission. */
data class CollaborationMatrixIdentity(
    val userId: String,
    val deviceId: String,
    val isGuest: Boolean,
)
