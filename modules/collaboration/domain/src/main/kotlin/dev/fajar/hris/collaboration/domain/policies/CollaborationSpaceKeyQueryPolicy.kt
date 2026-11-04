package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyClaim
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyQuery
import dev.fajar.hris.core.domain.*
import java.util.UUID

private val groupQueryDevice = Regex("HRISG?_[a-f0-9]{32}")
private val groupQueryUuid = Regex("[a-f0-9]{32}")

/** Separate from company query shape; snapshots retain no caller-owned collections. */
fun snapshotCollaborationSpaceKeyQuery(
    request: CollaborationMatrixKeyQuery
): Result<CollaborationMatrixKeyQuery> {
    val users = request.devices.entries.asSequence().take(101).toList()
    if (users.size !in 1..100) return invalidGroupKeyQuery()
    val captured = linkedMapOf<String, List<String>>()
    for ((user, requested) in users) {
        if (user.length !in 1..255 || user.any { it.code !in 33..126 })
            return invalidGroupKeyQuery()
        val devices = requested.asSequence().take(6).toList()
        if (
            devices.size > 5 ||
                devices.toSet().size != devices.size ||
                devices.any { !groupQueryDevice.matches(it) }
        )
            return invalidGroupKeyQuery()
        captured[user] = devices
    }
    if (captured.size != users.size) return invalidGroupKeyQuery()
    return Result.Success(CollaborationMatrixKeyQuery(captured))
}

fun snapshotCollaborationSpaceKeyClaim(
    request: CollaborationMatrixKeyClaim
): Result<CollaborationMatrixKeyClaim> {
    val users = request.oneTimeKeys.entries.asSequence().take(101).toList()
    if (users.size !in 1..100) return invalidGroupKeyClaim()
    val captured = linkedMapOf<String, Map<String, String>>()
    for ((user, requested) in users) {
        if (user.length !in 1..255 || user.any { it.code !in 33..126 })
            return invalidGroupKeyClaim()
        val devices = requested.entries.asSequence().take(6).map { it.key to it.value }.toList()
        if (
            devices.size !in 1..5 ||
                devices.any {
                    !groupQueryDevice.matches(it.first) || it.second != "signed_curve25519"
                } ||
                devices.map { it.first }.toSet().size != devices.size
        )
            return invalidGroupKeyClaim()
        captured[user] = devices.toMap()
    }
    if (captured.size != users.size) return invalidGroupKeyClaim()
    return snapshotCollaborationSpaceKeyQuery(
            CollaborationMatrixKeyQuery(captured.mapValues { it.value.keys.toList() })
        )
        .map { CollaborationMatrixKeyClaim(captured) }
}

/**
 * Names identify candidates only. Current canonical identity and exact room authority are separate.
 */
fun collaborationSpaceKeyQueryAccounts(
    request: CollaborationMatrixKeyQuery,
    spaceId: UUID,
    serverName: String,
): Result<Map<String, UUID>> {
    if (spaceId == UUID(0, 0) || !validCollaborationSpaceServer(serverName))
        return invalidGroupKeyQuery()
    val captured = snapshotCollaborationSpaceKeyQuery(request)
    if (captured is Result.Failed) return captured
    val suffix = ":$serverName"
    val groupPrefix = "@hrisg_${spaceId.toString().replace("-", "")}_"
    val result = linkedMapOf<String, UUID>()
    for (user in (captured as Result.Success).value.devices.keys) {
        if (!user.endsWith(suffix)) return invalidGroupKeyQuery()
        val local = user.removeSuffix(suffix)
        val account =
            when {
                local.startsWith(groupPrefix) -> local.removePrefix(groupPrefix)
                local.startsWith("@hris_") &&
                    local.length == 71 &&
                    local[38] == '_' &&
                    groupQueryUuid.matches(local.substring(6, 38)) &&
                    local.substring(6, 38) != "0".repeat(32) -> local.substring(39)
                else -> return invalidGroupKeyQuery()
            }
        if (!groupQueryUuid.matches(account)) return invalidGroupKeyQuery()
        val id =
            UUID.fromString(
                "${account.substring(0, 8)}-${account.substring(8, 12)}-${account.substring(12, 16)}-${account.substring(16, 20)}-${account.substring(20)}"
            )
        if (id == UUID(0, 0)) return invalidGroupKeyQuery()
        result[user] = id
    }
    // Keep several requested aliases for one account; select keys by exact stored user ID later.
    return Result.Success(result)
}

private fun invalidGroupKeyQuery() =
    Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_key_query"))

private fun invalidGroupKeyClaim() =
    Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_key_claim"))
