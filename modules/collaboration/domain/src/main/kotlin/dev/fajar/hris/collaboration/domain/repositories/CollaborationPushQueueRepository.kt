package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Caller owns transactions, current background admission and target revalidation. */
interface CollaborationPushQueueRepository {
    /** True advances durable wake identity; false is an already represented hint. */
    fun append(
        binding: CollaborationPushBinding,
        event: CollaborationPushEvent?,
        lifetimeSeconds: Int,
    ): Result<Boolean>

    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean = false,
    ): Result<CollaborationPushHead?>

    /** Worker-only bounded global claim. Returned metadata identifies subsequent admission. */
    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<CollaborationPushHead>>

    /** Acknowledge only the captured revision, retaining later callbacks as pending. */
    fun finish(lease: CollaborationPushLease): Result<Boolean>

    fun defer(lease: CollaborationPushLease, availableAt: Instant): Result<Boolean>
}
