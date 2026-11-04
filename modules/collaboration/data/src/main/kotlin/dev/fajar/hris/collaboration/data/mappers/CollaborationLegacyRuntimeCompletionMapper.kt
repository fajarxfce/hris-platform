package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixDeviceId
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeCompletionsRecord
import java.time.Instant
import java.time.OffsetDateTime

fun legacyRuntimeCompletionFromRow(
    row: CollaborationLegacyRuntimeCompletionsRecord,
    owner: CollaborationLegacyRuntimeOwner,
): CollaborationLegacyRuntimeCompletion {
    require(
        row.legacyCompanyId == owner.companyId &&
            row.legacyDeviceId == owner.deviceId &&
            row.accountId == owner.accountId &&
            row.nativeSessionId == owner.nativeSessionId
    )
    val target =
        CollaborationLegacyRuntimeTarget(requireNotNull(row.spaceId), requireNotNull(row.deviceId))
    val user = requireNotNull(row.matrixUserId)
    val prefix = "@${collaborationMatrixLocalpart(owner.companyId,owner.accountId)}:"
    require(
        user.startsWith(prefix) &&
            user.length in (prefix.length + 1)..255 &&
            !user.any { it.isWhitespace() }
    )
    val device = requireNotNull(row.matrixDeviceId)
    require(device == collaborationMatrixDeviceId(owner.deviceId))
    val fingerprint = requireNotNull(row.fingerprint)
    require(fingerprint.matches(Regex("[a-f0-9]{64}")))
    val nativeVersion = requireNotNull(row.nativeSessionVersion)
    val revision = requireNotNull(row.provisioningRevision)
    val sourceDeviceVersion = requireNotNull(row.sourceDeviceVersion)
    val sourceGeneration = requireNotNull(row.sourceProviderGeneration)
    val targetDeviceVersion = requireNotNull(row.targetDeviceVersion)
    val targetGeneration = requireNotNull(row.targetProviderGeneration)
    require(
        nativeVersion >= 0 &&
            revision in 0..999999 &&
            sourceDeviceVersion in 1..1000000 &&
            sourceGeneration in 1..1000000 &&
            targetDeviceVersion in 1..999999 &&
            targetGeneration in 0..999999
    )
    val gateway =
        row.sourceGatewayId?.let { id ->
            val version = requireNotNull(row.sourceGatewayVersion)
            require(version in 1..1000000)
            CollaborationLegacyGatewayRetirement(id, version)
        }
    require(gateway != null || row.sourceGatewayVersion == null)
    val frozen = time(row.frozenAt)
    val observed = time(row.keysObservedAt)
    val completed = time(row.completedAt)
    require(frozen <= observed && observed <= completed)
    return CollaborationLegacyRuntimeCompletion(
        owner,
        target,
        user,
        device,
        fingerprint,
        nativeVersion,
        revision,
        sourceDeviceVersion,
        sourceGeneration,
        gateway,
        targetDeviceVersion,
        targetGeneration,
        frozen,
        observed,
        completed,
    )
}

private fun time(value: OffsetDateTime?): Instant {
    val at = requireNotNull(value).toInstant()
    require(at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L)
    return at
}
