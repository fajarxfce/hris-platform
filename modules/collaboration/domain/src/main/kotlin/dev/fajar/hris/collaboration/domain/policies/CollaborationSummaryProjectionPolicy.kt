package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*

/** Peer employment status does not withdraw a conversation's owned public metadata. */
fun projectCollaborationSummary(
    candidate: CollaborationSummaryCandidate,
    current: List<CollaborationSummaryRecord>,
): Result<List<CollaborationSummaryRecord>> {
    val owners = setOf(candidate.firstAccountId, candidate.secondAccountId)
    if (
        owners.size != 2 ||
            current.size > 2 ||
            listOf(candidate.firstDisplayName, candidate.secondDisplayName).any {
                it.isBlank() || it.length > 200
            } ||
            current.map { it.ownerAccountId }.distinct().size != current.size ||
            current.any {
                it.target != candidate.target ||
                    it.ownerAccountId !in owners ||
                    it.peerAccountId !in owners ||
                    it.ownerAccountId == it.peerAccountId ||
                    it.version < 0 ||
                    it.peerDisplayName?.let { name -> name.isBlank() || name.length > 200 } == true
            }
    )
        return Result.Failed(
            Failure(FailureKind.UNEXPECTED, "collaboration_summary_projection_invalid")
        )
    val changes = mutableListOf<CollaborationSummaryRecord>()
    for (owner in listOf(candidate.firstAccountId, candidate.secondAccountId)) {
        val peer =
            if (owner == candidate.firstAccountId) candidate.secondAccountId
            else candidate.firstAccountId
        val name =
            if (owner == candidate.firstAccountId) candidate.secondDisplayName
            else candidate.firstDisplayName
        val before = current.singleOrNull { it.ownerAccountId == owner }
        if (before?.peerDisplayName == name) continue
        if (before?.version == Long.MAX_VALUE)
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "collaboration_summary_revision_exhausted")
            )
        changes +=
            CollaborationSummaryRecord(
                candidate.target,
                owner,
                peer,
                before?.version?.plus(1) ?: 0,
                name,
            )
    }
    return Result.Success(changes.toList())
}
