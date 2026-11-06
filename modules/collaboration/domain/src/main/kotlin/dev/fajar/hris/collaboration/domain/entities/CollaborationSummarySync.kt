package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** The stable native parent survives access-token rotation; it is not local cache ownership. */
data class CollaborationSummaryOwner(val companyId: UUID, val accountId: UUID, val parentId: UUID)

data class CollaborationSummaryScope(
    val owner: CollaborationSummaryOwner,
    val credentialVersion: Long,
    val companyVersion: Long,
    val membershipVersion: Long,
)

data class CollaborationSummaryHead(val epoch: UUID, val position: Long, val prunedThrough: Long)

enum class CollaborationSummaryPhase {
    BOOTSTRAP,
    CHANGES,
}

data class CollaborationSummaryCursor(
    val scope: CollaborationSummaryScope,
    val epoch: UUID,
    val phase: CollaborationSummaryPhase,
    val position: Long,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val upperPosition: Long? = null,
    val after: UUID? = null,
    val page: Int = 0,
)

/** Null summary is a versioned tombstone; canonical conversation.version stays immutable. */
data class CollaborationSummaryFact(
    val conversationId: UUID,
    val revision: Long,
    val summary: CollaborationDirectSummary?,
)

data class CollaborationSummaryReference(
    val position: Long,
    val conversationId: UUID,
    val revision: Long,
)

data class CollaborationSummaryChange(val position: Long, val fact: CollaborationSummaryFact)

data class CollaborationSummaryBootstrap(
    val items: List<CollaborationSummaryFact>,
    val nextPage: String?,
    val changesCursor: String?,
    val pending: Boolean,
    val serverTime: Instant,
)

data class CollaborationSummaryChanges(
    val items: List<CollaborationSummaryChange>,
    val nextCursor: String,
    val hasMore: Boolean,
    val pending: Boolean,
    val serverTime: Instant,
)
