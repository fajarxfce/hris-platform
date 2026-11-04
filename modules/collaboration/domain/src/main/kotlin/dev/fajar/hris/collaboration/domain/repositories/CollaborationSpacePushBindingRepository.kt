package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Caller owns transactions, ordered current admission and post-provider publication checks. */
interface CollaborationSpacePushBindingRepository {
    fun lock(spaceId: UUID, deviceId: UUID): Result<Unit>

    /** Own retained metadata remains readable after offboarding and native-parent purge. */
    fun find(spaceId: UUID, accountId: UUID, deviceId: UUID): Result<CollaborationSpacePushBinding?>

    /** Exact anonymous lookup only identifies the next current account/device admission. */
    fun findKey(key: CollaborationSpacePushKey): Result<CollaborationSpacePushBinding?>

    /** Commit stable capability before provider registration; a lost response never resets it. */
    fun reserve(binding: CollaborationSpacePushBinding): Result<Unit>

    fun key(binding: CollaborationSpacePushBinding): Result<CollaborationSpacePushKey?>

    /** Acknowledge exactly the original immutable reservation, without replacing its capability. */
    fun publish(expected: CollaborationSpacePushBinding, at: Instant): Result<Unit>
}
