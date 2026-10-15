package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveCarryValidity
import java.time.*

/**
 * Null means no additional carry expiry; configured months are complete destination-year months.
 */
fun leaveCarryValidity(
    destinationYear: Int,
    months: Int?,
    timezone: String,
): Result<LeaveCarryValidity?> {
    if (destinationYear !in 1901..2200 || (months != null && months !in 1..12))
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_carry_policy"))
    if (months == null) return Result.Success(null)
    return try {
        val boundary = LocalDate.of(destinationYear, 1, 1).plusMonths(months.toLong())
        Result.Success(
            LeaveCarryValidity(
                boundary.minusDays(1),
                boundary.atStartOfDay(ZoneId.of(timezone)).withLaterOffsetAtOverlap().toInstant(),
            )
        )
    } catch (_: DateTimeException) {
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_carry_policy"))
    }
}
