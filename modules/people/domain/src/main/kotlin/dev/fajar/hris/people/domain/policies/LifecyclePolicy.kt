package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.LocalDate

fun validateLifecycleTemplate(template: LifecycleTemplate, reason: String): Result<Unit> {
    if (
        !template.code.matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}")) ||
            template.name.isBlank() ||
            template.name.length > 120 ||
            template.version < 0 ||
            template.tasks.isEmpty() ||
            template.tasks.size > 64 ||
            template.tasks.map { it.key }.distinct().size != template.tasks.size ||
            template.tasks.any {
                !it.key.matches(Regex("[a-z][a-z0-9_-]{0,47}")) ||
                    it.title.isBlank() ||
                    it.title.length > 160 ||
                    it.dueDays !in -90..365
            } ||
            reason.isBlank() ||
            reason.length > 1000
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_template"))
    return Result.Success(Unit)
}

fun validateLifecycleCompletion(
    case: LifecycleCase,
    version: Long,
    kind: LifecycleKind,
    reason: String,
): Result<Unit> {
    if (version < 0 || reason.isBlank() || reason.length > 1000)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_change"))
    if (case.version != version)
        return Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
    if (case.status != LifecycleStatus.OPEN || case.kind != kind)
        return Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open"))
    if (case.tasks.any { it.required && it.status != LifecycleTaskStatus.DONE })
        return Result.Failed(Failure(FailureKind.CONFLICT, "required_lifecycle_tasks_pending"))
    if (case.tasks.any { it.status == LifecycleTaskStatus.PENDING })
        return Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_tasks_unresolved"))
    return Result.Success(Unit)
}

fun validateLifecycleDate(date: LocalDate, reason: String): Result<Unit> =
    if (date.year !in 1900..2200 || reason.isBlank() || reason.length > 1000)
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_case"))
    else Result.Success(Unit)
