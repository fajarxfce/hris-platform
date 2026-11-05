package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

data class CollaborationSummaryTarget(val companyId: UUID, val conversationId: UUID)

data class CollaborationSummaryAccountWork(
    val accountId: UUID,
    val after: CollaborationSummaryTarget?,
)

/** Minimal public source projection from one database statement. No profile or message content. */
data class CollaborationSummaryCandidate(
    val target: CollaborationSummaryTarget,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val firstDisplayName: String,
    val secondDisplayName: String,
)

/** Metadata revisions are independent of the immutable canonical conversation version. */
data class CollaborationSummaryRecord(
    val target: CollaborationSummaryTarget,
    val ownerAccountId: UUID,
    val peerAccountId: UUID,
    val version: Long,
    val peerDisplayName: String?,
)

data class CollaborationSummaryRefresh(
    val expandedTargets: Int,
    val projectedPairs: Int,
    val changedRecords: Int,
)
