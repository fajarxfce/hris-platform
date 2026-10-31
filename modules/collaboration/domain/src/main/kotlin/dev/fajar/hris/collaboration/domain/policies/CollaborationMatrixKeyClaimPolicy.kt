package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*

fun snapshotCollaborationKeyClaim(
    request: CollaborationMatrixKeyClaim
): Result<CollaborationMatrixKeyClaim> {
    if (
        request.oneTimeKeys.size !in 1..100 ||
            request.oneTimeKeys.values.any {
                it.size !in 1..5 || it.values.any { algorithm -> algorithm != "signed_curve25519" }
            }
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_key_claim"))
    val captured = request.oneTimeKeys.mapValues { it.value.toMap() }
    return snapshotCollaborationKeyQuery(
            CollaborationMatrixKeyQuery(captured.mapValues { it.value.keys.toList() })
        )
        .map { CollaborationMatrixKeyClaim(captured) }
}
