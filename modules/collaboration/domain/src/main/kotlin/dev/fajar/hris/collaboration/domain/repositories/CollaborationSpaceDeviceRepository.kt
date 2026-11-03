package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceDevice
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Caller owns the account/parent guards and the transaction with receipt/journal writes. */
interface CollaborationSpaceDeviceRepository {
    /** Owned security metadata survives parent purge/offboarding; not content admission. */
    fun metadata(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean = false,
    ): Result<CollaborationSpaceDevice?>

    /**
     * Exact observed revision; native/group admission and journal are the caller's responsibility.
     */
    fun replace(device: CollaborationSpaceDevice, expectedVersion: Long): Result<Unit>

    fun findBound(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): Result<CollaborationSpaceDevice?>

    /** Counts distinct live native parents across legacy/group devices, capped at six. */
    fun otherActiveParents(
        spaceId: UUID,
        accountId: UUID,
        excluding: UUID,
        at: Instant,
    ): Result<Int>

    /** Inserts the initial device and its exact immutable native binding together. */
    fun register(device: CollaborationSpaceDevice): Result<Unit>
}
