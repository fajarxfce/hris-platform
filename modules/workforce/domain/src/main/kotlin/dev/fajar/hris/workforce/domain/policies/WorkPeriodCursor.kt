package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import java.util.UUID

fun parseWorkPeriodCursor(values: Map<String, String>): Result<UUID?> {
    val text = values["afterEmployeeId"] ?: return Result.Success(null)
    if (!text.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        return Result.Failed(Failure(FailureKind.UNEXPECTED, "invalid_work_period_checkpoint"))
    return Result.Success(UUID.fromString(text))
}
