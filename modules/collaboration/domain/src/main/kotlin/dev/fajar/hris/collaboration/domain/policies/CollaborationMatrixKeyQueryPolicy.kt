package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyQuery
import dev.fajar.hris.core.domain.*
import java.util.UUID

private val queryDevice = Regex("HRIS_[a-f0-9]{32}")
private val queryAccount = Regex("[a-f0-9]{32}")

fun snapshotCollaborationKeyQuery(
    request: CollaborationMatrixKeyQuery
): Result<CollaborationMatrixKeyQuery> {
    if (request.devices.size !in 1..100) return invalidKeyQuery()
    for ((user, devices) in request.devices) {
        if (
            user.length !in 1..255 ||
                user.any { it.code !in 33..126 } ||
                devices.size > 5 ||
                devices.toSet().size != devices.size ||
                devices.any { !queryDevice.matches(it) }
        )
            return invalidKeyQuery()
    }
    return Result.Success(
        CollaborationMatrixKeyQuery(request.devices.mapValues { it.value.toList() })
    )
}

/** Canonical names only; foreign companies/servers are rejected before account guards or I/O. */
fun collaborationKeyQueryAccounts(
    request: CollaborationMatrixKeyQuery,
    companyId: UUID,
    serverName: String,
): Result<Map<String, UUID>> {
    val snapshot = snapshotCollaborationKeyQuery(request)
    if (snapshot is Result.Failed) return snapshot
    val prefix = "@hris_${companyId.toString().replace("-", "")}_"
    val suffix = ":$serverName"
    val result = linkedMapOf<String, UUID>()
    for (user in (snapshot as Result.Success).value.devices.keys) {
        if (!user.startsWith(prefix) || !user.endsWith(suffix)) return invalidKeyQuery()
        val account = user.removePrefix(prefix).removeSuffix(suffix)
        if (!queryAccount.matches(account)) return invalidKeyQuery()
        result[user] =
            UUID.fromString(
                "${account.substring(0, 8)}-${account.substring(8, 12)}-${account.substring(12, 16)}-${account.substring(16, 20)}-${account.substring(20)}"
            )
    }
    return Result.Success(result)
}

private fun invalidKeyQuery() =
    Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_key_query"))
