package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

enum class CollaborationPlatform {
    ANDROID,
    IOS,
    WEB,
}

data class CollaborationRegistration(
    val deviceId: UUID,
    val companyId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val platform: CollaborationPlatform,
    val label: String,
    val createdAt: Instant,
)
