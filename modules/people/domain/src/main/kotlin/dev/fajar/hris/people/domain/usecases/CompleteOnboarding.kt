package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class CompleteOnboarding(
    private val lifecycle: LifecycleRepository,
    private val people: PeopleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.lifecycle.manage")
        if (access is Result.Failed) return access
        if (expectedVersion < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_change"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.onboarding_complete",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val structure = people.lockReportingLines(company)
            if (structure is Result.Failed) return@run structure
            val lock = lifecycle.lockCase(company, id)
            if (lock is Result.Failed) return@run lock
            val result = lifecycle.case(company, id)
            if (result is Result.Failed) return@run result
            val case =
                (result as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found")
                    )
            val valid =
                validateLifecycleCompletion(case, expectedVersion, LifecycleKind.ONBOARDING, reason)
            if (valid is Result.Failed) return@run valid
            val event =
                LifecycleEvent(
                    case.version + 1,
                    null,
                    LifecycleAction.COMPLETED,
                    null,
                    actor.accountId,
                    reason,
                    clock.instant(),
                )
            lifecycle.finish(actor, id, case.version, LifecycleStatus.COMPLETED, event).flatMap {
                receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "lifecycle_case",
                                id,
                                "people.onboarding_completed",
                                reason = reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
