package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import java.util.UUID

interface NativePushRegistrationRepository {
    fun find(accountId: UUID, sessionId: UUID): Result<NativePushRegistration?>

    fun save(
        registration: NativePushRegistration,
        token: String?,
        expectedVersion: Long?,
    ): Result<Boolean>
}
