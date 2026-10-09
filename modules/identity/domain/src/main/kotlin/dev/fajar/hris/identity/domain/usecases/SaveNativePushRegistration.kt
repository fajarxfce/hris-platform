package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class SaveNativePushRegistration(
    private val registrations: NativePushRegistrationRepository,
    private val sessions: NativeSessionRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        operationId: UUID,
        input: SaveNativePushRegistrationCommand,
    ): Result<MutationReceipt> {
        val validation = validateNativePushInput(input)
        if (validation is Result.Failed) return validation
        val owner = actor.copy(companyId = null)
        // Only the fingerprint is persisted by the operation repository; no token in
        // receipts/audit.
        val key =
            OperationKey(
                "identity.push_registration_saved",
                operationId,
                listOf(
                    sessionId.toString(),
                    input.expectedVersion?.toString(),
                    input.platform.name,
                    input.token,
                ),
            )
        return transactions.run(owner) {
            val replay = operations.lockAndReplay(owner, key)
            if (replay is Result.Failed) return@run replay
            // Match native refresh/revocation: account before session before registration.
            val account = sessions.lockAccount(owner.accountId)
            if (account is Result.Failed) return@run account
            val found = sessions.lock(sessionId, owner.accountId)
            if (found is Result.Failed) return@run found
            val session = (found as Result.Success).value
            val checked =
                validateNativePushActor(
                    owner,
                    (account as Result.Success).value,
                    session,
                    sessionVersion,
                    clock.instant(),
                )
            if (checked is Result.Failed) return@run checked
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val loaded = registrations.find(owner.accountId, sessionId)
            if (loaded is Result.Failed) return@run loaded
            val previous = (loaded as Result.Success).value
            if (previous?.version != input.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous != null && previous.version >= 9999)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "push_registration_version_limit")
                )
            val now =
                maxOf(
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                    previous?.updatedAt ?: requireNotNull(session).createdAt,
                )
            val registration =
                NativePushRegistration(
                    sessionId,
                    owner.accountId,
                    input.platform,
                    true,
                    (previous?.version ?: -1) + 1,
                    previous?.registeredAt ?: now,
                    now,
                    minOf(requireNotNull(session).expiresAt, now.plus(30, ChronoUnit.DAYS)),
                )
            registrations.save(registration, input.token, previous?.version).flatMap { saved ->
                if (!saved) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else {
                    val receipt = MutationReceipt(sessionId, registration.version)
                    operations
                        .record(owner, key, receipt)
                        .flatMap {
                            journal.record(
                                owner,
                                ChangeRecord(
                                    "native_push_registration",
                                    sessionId,
                                    "identity.push_registration_saved",
                                ),
                            )
                        }
                        .map { receipt }
                }
            }
        }
    }
}
