package dev.fajar.hris.administration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuditSearchPolicyTest {
    private val now = Instant.parse("2026-10-01T00:00:00.123456789Z")

    @Test
    fun defaultsAndExplicitBoundsUseFiniteUtcWindowsAndDatabasePrecision() {
        val default = (prepareAuditQuery(AuditSearch(), now) as Result.Success).value
        assertEquals(now.truncatedTo(ChronoUnit.MICROS), default.until)
        assertEquals(default.until.minus(Duration.ofDays(30)), default.from)
        assertEquals(50, default.limit)
        assertNull(default.before)
        assertInstanceOf(
            Result.Success::class.java,
            prepareAuditQuery(AuditSearch(from = now.minus(Duration.ofDays(90)), limit = 200), now),
        )
        for (search in
            listOf(
                AuditSearch(until = Instant.MIN),
                AuditSearch(until = Instant.MAX),
                AuditSearch(from = Instant.MIN),
                AuditSearch(from = now, until = now),
                AuditSearch(until = now.plusSeconds(1)),
                AuditSearch(from = now.minus(Duration.ofDays(91))),
            )) {
            val result = prepareAuditQuery(search, now) as Result.Failed
            assertEquals("invalid_audit_range", result.failure.code)
            assertEquals("90", result.failure.parameters["maximumDays"])
        }
    }

    @Test
    fun paginationAndExactCodeFiltersRejectUnboundedOrMalformedInputs() {
        for (limit in listOf(Int.MIN_VALUE, -1, 0, 201, Int.MAX_VALUE)) {
            assertEquals(
                "invalid_pagination",
                (prepareAuditQuery(AuditSearch(limit = limit), now) as Result.Failed).failure.code,
            )
        }
        for (search in
            listOf(
                AuditSearch(action = ""),
                AuditSearch(action = "a".repeat(101)),
                AuditSearch(resourceType = "a".repeat(81)),
                AuditSearch(action = "employee' OR 1=1"),
                AuditSearch(resourceType = " employee"),
            )) {
            assertEquals(
                "invalid_audit_filter",
                (prepareAuditQuery(search, now) as Result.Failed).failure.code,
            )
        }
        assertInstanceOf(
            Result.Success::class.java,
            prepareAuditQuery(
                AuditSearch(action = "people.employment_revised", resourceType = "employment"),
                now,
            ),
        )
    }

    @Test
    fun cursorMustBeInsideItsOriginalWindowAndMatchEveryActiveFilter() {
        val event =
            AuditEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "employment",
                UUID.randomUUID(),
                "people.employment_revised",
                UUID.randomUUID(),
                now.minusSeconds(1),
            )
        val query =
            (prepareAuditQuery(
                    AuditSearch(
                        actorId = event.actorId,
                        resourceType = event.resourceType,
                        resourceId = event.resourceId,
                        action = event.action,
                    ),
                    now,
                )
                    as Result.Success)
                .value
        assertEquals(Result.Success(query.copy(before = event)), validateAuditCursor(query, event))
        for (cursor in
            listOf(
                null,
                event.copy(recordedAt = query.until),
                event.copy(recordedAt = query.from.minusNanos(1)),
                event.copy(actorId = UUID.randomUUID()),
                event.copy(resourceId = UUID.randomUUID()),
                event.copy(resourceType = "payroll_run"),
                event.copy(action = "people.created"),
            )) {
            assertEquals(
                "invalid_audit_cursor",
                (validateAuditCursor(query, cursor) as Result.Failed).failure.code,
            )
        }
        assertInstanceOf(
            Result.Success::class.java,
            validateAuditCursor(query, event.copy(recordedAt = query.from)),
        )
    }
}
