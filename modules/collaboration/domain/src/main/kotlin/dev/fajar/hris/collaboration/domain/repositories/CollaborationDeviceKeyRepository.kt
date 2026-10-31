package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationDeviceKeyPublication
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

interface CollaborationDeviceKeyRepository {
    /** After the native parent/device/gateway guards; never held during provider I/O. */
    fun lock(companyId: UUID, deviceId: UUID): Result<Unit>

    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationDeviceKeyPublication?>

    fun reserve(value: CollaborationDeviceKeyPublication): Result<Unit>

    /** Exact reserved identity, version zero. The caller owns admission and provider readback. */
    fun publish(value: CollaborationDeviceKeyPublication, at: Instant): Result<Unit>
}
