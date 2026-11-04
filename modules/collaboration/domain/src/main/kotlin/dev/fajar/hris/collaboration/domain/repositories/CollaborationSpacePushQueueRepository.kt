package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Caller owns transactions, current background admission and target revalidation. */
interface CollaborationSpacePushQueueRepository {
    /** True advances durable wake identity; false is an already represented hint. */
    fun append(
        binding: CollaborationSpacePushBinding,
        event: CollaborationPushEvent?,
        lifetimeSeconds: Int,
    ): Result<Boolean>

    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean = false,
    ): Result<CollaborationSpacePushHead?>

    /** Worker-only bounded global claim. Returned metadata identifies subsequent admission. */
    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<CollaborationSpacePushHead>>

    /** Acknowledge only the captured revision, retaining later callbacks as pending. */
    fun finish(lease: CollaborationSpacePushLease): Result<Boolean>

    fun defer(lease: CollaborationSpacePushLease, availableAt: Instant): Result<Boolean>
}
