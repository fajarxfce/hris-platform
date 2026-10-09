package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class DisableNativePushRegistration(
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
        expectedVersion: Long,
    ): Result<MutationReceipt> {
        if (expectedVersion !in 0..10000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val owner = actor.copy(companyId = null)
        val key =
            OperationKey(
                "identity.push_registration_disabled",
                operationId,
                listOf(sessionId.toString(), expectedVersion.toString()),
            )
        return transactions.run(owner) {
            val replay = operations.lockAndReplay(owner, key)
            if (replay is Result.Failed) return@run replay
            val account = sessions.lockAccount(owner.accountId)
            if (account is Result.Failed) return@run account
            val session = sessions.lock(sessionId, owner.accountId)
            if (session is Result.Failed) return@run session
            val checked =
                validateNativePushActor(
                    owner,
                    (account as Result.Success).value,
                    (session as Result.Success).value,
                    sessionVersion,
                    clock.instant(),
                )
            if (checked is Result.Failed) return@run checked
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val loaded = registrations.find(owner.accountId, sessionId)
            if (loaded is Result.Failed) return@run loaded
            val previous =
                (loaded as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "push_registration_not_found")
                    )
            if (previous.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (!previous.enabled) {
                val receipt = MutationReceipt(sessionId, previous.version)
                return@run operations.record(owner, key, receipt).map { receipt }
            }
            if (previous.version >= 10000)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "push_registration_version_limit")
                )
            val updated =
                previous.copy(
                    enabled = false,
                    version = previous.version + 1,
                    updatedAt =
                        maxOf(clock.instant().truncatedTo(ChronoUnit.MICROS), previous.updatedAt),
                )
            registrations.save(updated, null, expectedVersion).flatMap { saved ->
                if (!saved) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else {
                    val receipt = MutationReceipt(sessionId, updated.version)
                    operations
                        .record(owner, key, receipt)
                        .flatMap {
                            journal.record(
                                owner,
                                ChangeRecord(
                                    "native_push_registration",
                                    sessionId,
                                    "identity.push_registration_disabled",
                                ),
                            )
                        }
                        .map { receipt }
                }
            }
        }
    }
}
