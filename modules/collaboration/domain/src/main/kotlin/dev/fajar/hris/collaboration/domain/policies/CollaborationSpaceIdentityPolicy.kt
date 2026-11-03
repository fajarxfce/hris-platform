package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

fun validCollaborationSpaceServer(serverName: String): Boolean {
    if (serverName.length !in 1..180) return false
    val server = runCatching { URI("https://$serverName") }.getOrNull() ?: return false
    return server.host != null &&
        server.rawAuthority == serverName &&
        server.rawUserInfo == null &&
        server.rawPath == "" &&
        server.rawQuery == null &&
        server.rawFragment == null &&
        server.port in -1..65535 &&
        server.port != 0
}

fun collaborationSpaceMatrixLocalpart(spaceId: UUID, accountId: UUID): String =
    "hrisg_${spaceId.toString().replace("-", "")}_${accountId.toString().replace("-", "")}"

/** Retained user preference is supplied by one bounded, currently eligible source snapshot. */
fun selectCollaborationSpaceIdentity(
    spaceId: UUID,
    accountId: UUID,
    serverName: String,
    input: CollaborationSpaceIdentityInput?,
    at: Instant,
): Result<CollaborationSpaceIdentity> {
    if (!validCollaborationSpaceServer(serverName)) return invalidIdentity()
    if (input == null || input.spaceId != spaceId || input.accountId != accountId)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
    val selected = input.selected
    if (selected != null) {
        if (
            input.preferred != null ||
                selected.spaceId != spaceId ||
                selected.accountId != accountId ||
                selected.version != 0L ||
                selected.createdAt > at
        )
            return invalidIdentity()
        if (selected.serverName != serverName)
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "collaboration_identity_migration_required")
            )
        val prefix =
            when (selected.origin) {
                CollaborationSpaceIdentityOrigin.LEGACY ->
                    selected.legacy?.let { collaborationMatrixLocalpart(it.companyId, accountId) }
                        ?: return invalidIdentity()
                CollaborationSpaceIdentityOrigin.SPACE -> {
                    if (selected.legacy != null) return invalidIdentity()
                    collaborationSpaceMatrixLocalpart(spaceId, accountId)
                }
            }
        return if (selected.matrixUserId == "@$prefix:$serverName") Result.Success(selected)
        else invalidIdentity()
    }
    val legacy = input.preferred
    if (
        legacy != null &&
            (legacy.accountId != accountId ||
                legacy.identityObservedAt > at ||
                legacy.deviceCreatedAt > legacy.identityObservedAt ||
                legacy.matrixUserId !=
                    "@${collaborationMatrixLocalpart(legacy.position.companyId, accountId)}:$serverName" ||
                legacy.matrixDeviceId != collaborationMatrixDeviceId(legacy.position.deviceId))
    )
        return invalidIdentity()
    return Result.Success(
        CollaborationSpaceIdentity(
            spaceId,
            accountId,
            serverName,
            legacy?.matrixUserId
                ?: "@${collaborationSpaceMatrixLocalpart(spaceId, accountId)}:$serverName",
            if (legacy == null) CollaborationSpaceIdentityOrigin.SPACE
            else CollaborationSpaceIdentityOrigin.LEGACY,
            legacy?.position,
            at.truncatedTo(ChronoUnit.MICROS),
        )
    )
}

private fun invalidIdentity() =
    Result.Failed(Failure(FailureKind.CONFLICT, "invalid_collaboration_identity"))
