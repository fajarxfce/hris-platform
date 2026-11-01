package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Callers own current admission, transactions and the per-device guard. */
interface CollaborationPushBindingRepository {
    fun lock(companyId: UUID, deviceId: UUID): Result<Unit>

    fun find(companyId: UUID, accountId: UUID, deviceId: UUID): Result<CollaborationPushBinding?>

    /**
     * Own auth-lookup transaction; the result only identifies the scope for subsequent admission.
     */
    fun findKey(key: CollaborationPushKey): Result<CollaborationPushBinding?>

    fun reserve(binding: CollaborationPushBinding): Result<Unit>

    fun key(binding: CollaborationPushBinding): Result<CollaborationPushKey?>

    fun publish(binding: CollaborationPushBinding, at: Instant): Result<Boolean>
}
