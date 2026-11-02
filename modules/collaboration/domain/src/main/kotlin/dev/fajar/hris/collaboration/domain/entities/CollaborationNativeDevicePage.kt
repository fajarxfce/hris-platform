package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Owned recovery references only. Each company/content operation requires separate admission. */
data class CollaborationNativeDevicePage(
    val accountId: UUID,
    val nativeSessionId: UUID,
    val devices: List<CollaborationNativeDeviceReference>,
    val nextAfterCompanyId: UUID?,
)

data class CollaborationNativeDeviceReference(
    val companyId: UUID,
    val deviceId: UUID,
    val version: Long,
    val state: CollaborationDeviceState,
    val platform: CollaborationPlatform,
)
