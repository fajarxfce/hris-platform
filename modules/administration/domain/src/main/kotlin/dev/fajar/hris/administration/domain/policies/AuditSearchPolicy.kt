package dev.fajar.hris.administration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

fun prepareAuditQuery(input: AuditSearch, now: Instant): Result<AuditQuery> {
    if (input.limit !in 1..200)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_pagination"))
    val until = (input.until ?: now).truncatedTo(ChronoUnit.MICROS)
    if (until <= Instant.parse("1900-01-01T00:00:00Z") || until.isAfter(now))
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_audit_range",
                parameters = mapOf("maximumDays" to "90"),
            )
        )
    val from = (input.from ?: until.minus(Duration.ofDays(30))).truncatedTo(ChronoUnit.MICROS)
    if (
        from < Instant.parse("1900-01-01T00:00:00Z") ||
            !from.isBefore(until) ||
            Duration.between(from, until) > Duration.ofDays(90)
    ) {
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_audit_range",
                parameters = mapOf("maximumDays" to "90"),
            )
        )
    }
    if (
        input.resourceType != null &&
            !Regex("[a-z][a-z0-9_.:-]{0,79}").matches(input.resourceType) ||
            input.action != null && !Regex("[a-z][a-z0-9_.:-]{0,99}").matches(input.action)
    ) {
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_audit_filter"))
    }
    return Result.Success(
        AuditQuery(
            from,
            until,
            input.actorId,
            input.resourceType,
            input.resourceId,
            input.action,
            input.limit,
        )
    )
}

/** An immutable cursor record must belong to the same window and filters as its continuation. */
fun validateAuditCursor(query: AuditQuery, cursor: AuditEvent?): Result<AuditQuery> {
    if (
        cursor == null ||
            cursor.recordedAt < query.from ||
            cursor.recordedAt >= query.until ||
            query.actorId != null && query.actorId != cursor.actorId ||
            query.resourceType != null && query.resourceType != cursor.resourceType ||
            query.resourceId != null && query.resourceId != cursor.resourceId ||
            query.action != null && query.action != cursor.action
    ) {
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_audit_cursor"))
    }
    return Result.Success(query.copy(before = cursor))
}
