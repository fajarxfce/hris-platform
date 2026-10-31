package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSyncCursorRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.IdentityKeyUnavailableException
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.util.concurrent.CancellationException
import javax.crypto.AEADBadTagException

class EncryptedCollaborationSyncCursorRepository(private val crypto: IdentityTokenDataSource) :
    CollaborationSyncCursorRepository {
    override fun seal(
        owner: CollaborationSyncOwner,
        position: CollaborationSyncPosition,
    ): Result<String> = safeCollaborationSyncCursorCall {
        validate(position)
        crypto.encrypt(
            purpose(owner),
            "${position.scopeDigest}\n${position.providerSince}\n${position.providerFilterId}",
        )
    }

    override fun open(
        owner: CollaborationSyncOwner,
        cursor: String,
    ): Result<CollaborationSyncPosition> = safeCollaborationSyncCursorCall {
        require(cursor.length in 1..4096 && cursor.all { it.code in 33..126 })
        val parts = crypto.decrypt(purpose(owner), cursor).split('\n')
        require(parts.size == 3)
        CollaborationSyncPosition(parts[0], parts[1], parts[2]).also(::validate)
    }

    private fun purpose(owner: CollaborationSyncOwner): String {
        require(owner.serverName.length in 1..255 && owner.serverName.all { it.code in 33..126 })
        return "collaboration.sync.v1:" +
            crypto.hash(
                "${owner.companyId}\n${owner.accountId}\n${owner.deviceId}\n${owner.serverName}"
            )
    }

    private fun validate(position: CollaborationSyncPosition) {
        require(position.scopeDigest.matches(Regex("[a-f0-9]{64}")))
        require(
            position.providerSince.length in 1..1024 &&
                position.providerSince.all { it.code in 33..126 }
        )
        require(
            position.providerFilterId.length in 1..128 &&
                position.providerFilterId.all { it.code in 33..126 }
        )
    }
}

/**
 * No token logging, retries, automatic store deletion or interpretation of unauthenticated data.
 */
fun <T> safeCollaborationSyncCursorCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: Exception) {
        if (error is InterruptedException || error is CancellationException) throw error
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Failed(
            when (error) {
                is IdentityKeyUnavailableException ->
                    Failure(FailureKind.CONFLICT, "collaboration_sync_reset_required")
                is IllegalArgumentException,
                is AEADBadTagException ->
                    Failure(FailureKind.VALIDATION, "invalid_collaboration_sync_cursor")
                else -> Failure(FailureKind.UNEXPECTED, "collaboration_sync_cursor_failed")
            }
        )
    }
