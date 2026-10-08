package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.mappers.toAccount
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.MfaRepository
import java.time.Instant
import java.util.UUID

class StoredMfaRepository(
    private val store: MfaStoreDataSource,
    private val crypto: MfaCryptoDataSource,
) : MfaRepository {
    override fun advanceSecurityVersion(accountId: UUID, expected: Long): Result<Long> =
        safeIdentityCall { store.advanceSecurityVersion(accountId, expected) }
            .flatMap {
                if (it == null)
                    Result.Failed(Failure(FailureKind.CONFLICT, "stale_security_version"))
                else Result.Success(it)
            }

    override fun available(): Boolean = crypto.available()

    override fun lock(accountId: UUID): Result<MfaCredential?> = safeIdentityCall {
        val account = store.lockAccount(accountId) ?: return@safeIdentityCall null
        val pending = store.enrollment(accountId)
        MfaCredential(
            account.toAccount(),
            pending?.let { PendingMfaEnrollment(it.operationId, it.expiresAt.toInstant()) },
            account.mfaLastCounter,
        )
    }

    override fun startEnrollment(
        account: Account,
        operationId: UUID,
        expiresAt: Instant,
    ): Result<MfaEnrollment> = safeIdentityCall {
        val secret = crypto.newSecret()
        store.saveEnrollment(account.id, operationId, crypto.encrypt(account.id, secret), expiresAt)
        MfaEnrollment(operationId, secret, account.email, expiresAt)
    }

    override fun enrollment(account: Account): Result<MfaEnrollment?> = safeIdentityCall {
        store.enrollment(account.id)?.let {
            MfaEnrollment(
                it.operationId,
                crypto.decrypt(account.id, it.secretEncrypted),
                account.email,
                it.expiresAt.toInstant(),
            )
        }
    }

    override fun takeAttempt(accountId: UUID, windowStart: Instant, maximum: Int): Result<Boolean> =
        safeIdentityCall {
            store.incrementAttempt(accountId, windowStart, maximum)
        }

    override fun matchEnrollment(accountId: UUID, code: String, at: Instant): Result<Long?> =
        safeIdentityCall {
            store.enrollment(accountId)?.let {
                crypto.matchCounter(crypto.decrypt(accountId, it.secretEncrypted), code, at)
            }
        }

    override fun activate(
        accountId: UUID,
        operationId: UUID,
        expectedSecurityVersion: Long,
        counter: Long,
    ): Result<Long> =
        safeIdentityCall {
                val pending = store.enrollment(accountId)
                if (pending?.operationId != operationId) return@safeIdentityCall null
                store
                    .activate(accountId, expectedSecurityVersion, pending.secretEncrypted, counter)
                    ?.also { store.removeEnrollment(accountId, operationId) }
            }
            .flatMap {
                if (it == null)
                    Result.Failed(Failure(FailureKind.CONFLICT, "mfa_enrollment_changed"))
                else Result.Success(it)
            }

    override fun matchAuthenticator(accountId: UUID, code: String, at: Instant): Result<Long?> =
        safeIdentityCall {
            store.account(accountId)?.mfaSecretEncrypted?.let {
                crypto.matchCounter(crypto.decrypt(accountId, it), code, at)
            }
        }

    override fun recordCounter(
        accountId: UUID,
        expectedSecurityVersion: Long,
        counter: Long,
    ): Result<Boolean> = safeIdentityCall {
        store.recordCounter(accountId, expectedSecurityVersion, counter)
    }

    override fun consumeRecovery(accountId: UUID, code: String, at: Instant): Result<Boolean> =
        safeIdentityCall {
            store.consumeRecovery(accountId, crypto.recoveryHash(code), at)
        }

    override fun replaceRecoveryCodes(
        accountId: UUID,
        at: Instant,
        count: Int,
    ): Result<List<String>> = safeIdentityCall {
        require(count in 1..20)
        val codes = List(count) { crypto.newRecoveryCode() }
        store.replaceRecovery(accountId, codes.map(crypto::recoveryHash), at)
        codes
    }
}
