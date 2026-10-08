package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class ChangeLifecycleTask(
    private val lifecycle: LifecycleRepository,
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
        command: LifecycleTaskChange,
    ): Result<MutationReceipt> {
        val manager = "people.lifecycle.manage" in actor.permissions
        if (
            actor.companyId == null ||
                (!manager && "people.lifecycle.perform" !in actor.permissions)
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "lifecycle_access_required"))
        if (
            command.expectedVersion < 0 ||
                command.reason.isBlank() ||
                command.reason.length > 1000 ||
                !taskKey.matches(Regex("[a-z][a-z0-9_-]{0,47}"))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_change"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.lifecycle_task_change",
                operationId,
                listOf(
                    id.toString(),
                    taskKey,
                    command.expectedVersion.toString(),
                    command.status.name,
                    command.reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = lifecycle.lockCase(company, id)
            if (lock is Result.Failed) return@run lock
            val result = lifecycle.case(company, id)
            if (result is Result.Failed) return@run result
            val case =
                (result as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found")
                    )
            val task =
                case.tasks.firstOrNull { it.key == taskKey }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_task_not_found")
                    )
            if (!manager && task.assigneeId != actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "lifecycle_task_not_assigned")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (case.version != command.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (case.status != LifecycleStatus.OPEN)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open"))
            if (command.status == LifecycleTaskStatus.WAIVED && (!manager || task.required))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "lifecycle_task_cannot_be_waived")
                )
            if (task.status == command.status)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_task_unchanged"))
            val now = clock.instant()
            val pending = command.status == LifecycleTaskStatus.PENDING
            val changed =
                task.copy(
                    status = command.status,
                    completedBy = if (pending) null else actor.accountId,
                    completedAt = if (pending) null else now,
                )
            val event =
                LifecycleEvent(
                    case.version + 1,
                    taskKey,
                    when (command.status) {
                        LifecycleTaskStatus.PENDING -> LifecycleAction.TASK_PENDING
                        LifecycleTaskStatus.DONE -> LifecycleAction.TASK_DONE
                        LifecycleTaskStatus.WAIVED -> LifecycleAction.TASK_WAIVED
                    },
                    task.assigneeId,
                    actor.accountId,
                    command.reason,
                    now,
                )
            lifecycle.changeTask(actor, id, case.version, changed, event).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "lifecycle_case",
                                id,
                                "people.lifecycle_task_changed",
                                mapOf("task" to taskKey, "status" to command.status.name),
                                command.reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
