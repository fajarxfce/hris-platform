package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.mappers.*
import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import dev.fajar.hris.identity.domain.repositories.NativePushRegistrationRepository
import java.util.UUID

class StoredNativePushRegistrationRepository(
    private val store: NativePushRegistrationDataSource,
    private val crypto: IdentityTokenDataSource,
) : NativePushRegistrationRepository {
    override fun find(accountId: UUID, sessionId: UUID): Result<NativePushRegistration?> =
        safeIdentityCall {
            store.find(accountId, sessionId)?.toNativePushRegistration()
        }

    override fun save(
        registration: NativePushRegistration,
        token: String?,
        expectedVersion: Long?,
    ): Result<Boolean> = safeIdentityCall {
        val record = registration.toRecord()
        require(registration.enabled == (token != null))
        record.tokenHash = token?.let(crypto::hash)
        record.tokenEncrypted =
            token?.let {
                crypto.encrypt(
                    "hris:push:${registration.accountId}:${registration.sessionId}:${registration.version}",
                    it,
                )
            }
        store.save(record, expectedVersion) == 1
    }
}
