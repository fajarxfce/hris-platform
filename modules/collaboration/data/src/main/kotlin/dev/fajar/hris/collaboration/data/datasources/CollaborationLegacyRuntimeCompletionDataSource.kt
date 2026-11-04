package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeCompletionsRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CompleteCollaborationLegacyRuntimeData(
    val spaceId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val nativeSessionVersion: Long,
    val deviceVersion: Long,
    val providerGeneration: Long,
    val provisioningRevision: Long,
    val legacyDeviceVersion: Long,
    val legacyProviderGeneration: Long,
    val legacyGatewayVersion: Long?,
    val fingerprint: String,
    val keysObservedAt: OffsetDateTime,
)

interface CollaborationLegacyRuntimeCompletionDataSource {
    fun find(
        owner: CollaborationLegacyRuntimeCoordinatesData
    ): CollaborationLegacyRuntimeCompletionsRecord?

    fun complete(request: CompleteCollaborationLegacyRuntimeData): Boolean
}
