package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.legacyRuntimeCompletionFromRow
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationLegacyRuntimeCompletionRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.time.ZoneOffset.UTC

class StoredCollaborationLegacyRuntimeCompletionRepository(
    private val source: CollaborationLegacyRuntimeCompletionDataSource
) : CollaborationLegacyRuntimeCompletionRepository {
    override fun find(
        owner: CollaborationLegacyRuntimeOwner
    ): Result<CollaborationLegacyRuntimeCompletion?> = safeDatabaseCall {
        source.find(owner.raw())?.let { legacyRuntimeCompletionFromRow(it, owner) }
    }

    override fun complete(
        request: CompleteCollaborationLegacyRuntimeRequest
    ): Result<CollaborationLegacyRuntimeCompletion> = safeDatabaseCall {
        val gatewayVersion = request.legacyGatewayVersion
        require(
            request.nativeSessionVersion >= 0 &&
                request.targetDeviceVersion in 1..999999 &&
                request.targetProviderGeneration in 0..999999 &&
                request.provisioningRevision in 0..999999 &&
                request.legacyDeviceVersion in 0..999999 &&
                request.legacyProviderGeneration in 0..999999 &&
                (gatewayVersion == null || gatewayVersion in 0..999999) &&
                request.fingerprint.matches(Regex("[a-f0-9]{64}")) &&
                request.keysObservedAt.nano % 1000 == 0 &&
                request.keysObservedAt.epochSecond in -62135596800L..253402300799L
        )
        require(
            source.complete(
                CompleteCollaborationLegacyRuntimeData(
                    request.target.spaceId,
                    request.target.deviceId,
                    request.owner.nativeSessionId,
                    request.nativeSessionVersion,
                    request.targetDeviceVersion,
                    request.targetProviderGeneration,
                    request.provisioningRevision,
                    request.legacyDeviceVersion,
                    request.legacyProviderGeneration,
                    gatewayVersion,
                    request.fingerprint,
                    request.keysObservedAt.atOffset(UTC),
                )
            )
        )
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value =
            legacyRuntimeCompletionFromRow(
                requireNotNull(source.find(request.owner.raw())),
                request.owner,
            )
        require(
            value.target == request.target &&
                value.nativeSessionVersion == request.nativeSessionVersion &&
                value.targetDeviceVersion == request.targetDeviceVersion &&
                value.targetProviderGeneration == request.targetProviderGeneration &&
                value.provisioningRevision == request.provisioningRevision &&
                value.sourceDeviceVersion == request.legacyDeviceVersion + 1 &&
                value.sourceProviderGeneration == request.legacyProviderGeneration + 1 &&
                value.sourceGateway?.version == gatewayVersion?.plus(1) &&
                value.fingerprint == request.fingerprint &&
                value.keysObservedAt == request.keysObservedAt
        )
        value
    }

    private fun CollaborationLegacyRuntimeOwner.raw() =
        CollaborationLegacyRuntimeCoordinatesData(companyId, deviceId, accountId, nativeSessionId)
}
