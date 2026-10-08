package dev.fajar.hris.identity.data.datasources

import java.time.Instant
import java.util.UUID

interface MfaCryptoDataSource {
    fun available(): Boolean

    fun newSecret(): String

    fun newRecoveryCode(): String

    fun encrypt(accountId: UUID, secret: String): String

    fun decrypt(accountId: UUID, encrypted: String): String

    fun matchCounter(secret: String, code: String, at: Instant): Long?

    fun recoveryHash(code: String): String
}
