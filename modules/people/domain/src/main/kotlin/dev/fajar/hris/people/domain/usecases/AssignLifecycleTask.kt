package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class AssignLifecycleTask(
    private val lifecycle: LifecycleRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        taskKey: String,
        expectedVersion: Long,
        assignee: UUID?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.lifecycle.manage")
        if (access is Result.Failed) return access
        if (
            expectedVersion < 0 ||
                reason.isBlank() ||
                reason.length > 1000 ||
                !taskKey.matches(Regex("[a-z][a-z0-9_-]{0,47}"))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_assignment"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.lifecycle_task_assign",
                operationId,
                listOf(
                    id.toString(),
                    taskKey,
                    expectedVersion.toString(),
                    assignee?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val lock = lifecycle.lockCase(company, id)
            if (lock is Result.Failed) return@run lock
            val result = lifecycle.case(company, id)
            if (result is Result.Failed) return@run result
            val case =
                (result as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found")
                    )
            if (case.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (case.status != LifecycleStatus.OPEN)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open"))
            val task =
                case.tasks.firstOrNull { it.key == taskKey }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_task_not_found")
                    )
            if (task.status != LifecycleTaskStatus.PENDING || task.assigneeId == assignee)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "lifecycle_task_not_assignable")
                )
            if (assignee != null) {
                val found = members.find(company, assignee)
                if (found is Result.Failed) return@run found
                val member = (found as Result.Success).value
                if (
                    member == null ||
                        !member.accountActive ||
                        !member.membershipActive ||
                        member.permissions.none {
                            it == "people.lifecycle.perform" || it == "people.lifecycle.manage"
                        }
                )
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "lifecycle_assignee_unavailable")
                    )
            }
            val event =
                LifecycleEvent(
                    case.version + 1,
                    taskKey,
                    LifecycleAction.ASSIGNED,
                    assignee,
                    actor.accountId,
                    reason,
                    clock.instant(),
                )
            lifecycle
                .changeTask(actor, id, case.version, task.copy(assigneeId = assignee), event)
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "lifecycle_case",
                                    id,
                                    "people.lifecycle_task_assigned",
                                    mapOf("task" to taskKey),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
