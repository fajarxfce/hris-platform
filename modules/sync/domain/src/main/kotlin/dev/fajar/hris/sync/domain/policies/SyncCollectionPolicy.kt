package dev.fajar.hris.sync.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.SyncCollection

/** A client selection can narrow its owned projection; it never grants access. */
fun selectSyncCollections(
    permissions: Set<String>,
    requested: Set<SyncCollection>?,
): Result<Set<SyncCollection>> {
    if (requested?.isEmpty() == true)
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_sync_collections",
                fields = mapOf("collections" to "required"),
            )
        )
    val authorized = selfSyncCollections(permissions)
    val selected = requested?.intersect(authorized) ?: authorized
    if (selected.isEmpty())
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "sync_access_denied"))
    return Result.Success(selected)
}
