package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Current native device and published room coordinates; never a reusable sending credential. */
data class CollaborationDirectTransport(
    val companyId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val deviceId: UUID,
    val deviceVersion: Long,
    val platform: CollaborationPlatform,
    val conversationId: UUID,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val matrixRoomId: String,
    val roomProvisioningVersion: Long,
    val recipientReady: Boolean,
)
