package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AttendanceExceptionPolicyTest {
    private val company = UUID.fromString("10000000-0000-4000-8000-000000000001")
    private val from = LocalDate.parse("2026-09-01")
    private val until = LocalDate.parse("2026-10-01")
    private val entry =
        AttendanceException(
            UUID.fromString("20000000-0000-4000-8000-000000000001"),
            "E001",
            "Example",
            LocalDate.parse("2026-09-30"),
            1,
            false,
            null,
            Instant.parse("2026-10-01T03:00:00Z"),
        )

    @Test
    fun cursorsRoundTripTheirFilterAndPermitTheBeginningOfARead() {
        for (kind in
            listOf(null, AttendanceExceptionKind.PENDING, AttendanceExceptionKind.INCOMPLETE)) {
            assertEquals(
                Result.Success<AttendanceExceptionCursor?>(null),
                parseAttendanceExceptionCursor(company, from, until, kind, null),
            )
            val token = attendanceExceptionCursor(company, from, until, kind, entry)
            assertEquals(
                Result.Success(AttendanceExceptionCursor(entry.workDate, entry.employeeId)),
                parseAttendanceExceptionCursor(company, from, until, kind, token),
            )
        }
    }

    @Test
    fun changingCompanyWindowOrKindInvalidatesContinuation() {
        val token = attendanceExceptionCursor(company, from, until, null, entry)
        for (result in
            listOf(
                parseAttendanceExceptionCursor(UUID.randomUUID(), from, until, null, token),
                parseAttendanceExceptionCursor(company, from.plusDays(1), until, null, token),
                parseAttendanceExceptionCursor(company, from, until.minusDays(1), null, token),
                parseAttendanceExceptionCursor(
                    company,
                    from,
                    until,
                    AttendanceExceptionKind.PENDING,
                    token,
                ),
            )) assertEquals("invalid_page", (result as Result.Failed).failure.code)
    }

    @Test
    fun malformedOrOutOfWindowPositionsCannotExpandTheQuery() {
        val prefix = "1:$company:$from:$until:ALL:"
        for (token in
            listOf(
                "",
                "x".repeat(161),
                prefix,
                "$prefix${entry.workDate}",
                "${prefix}2026-02-30:${entry.employeeId}",
                "${prefix}2026-08-31:${entry.employeeId}",
                "${prefix}2026-10-02:${entry.employeeId}",
                "${prefix}${entry.workDate}:1-1-1-1-1",
                "${prefix}${entry.workDate}:${entry.employeeId}:extra",
                "${prefix}${entry.workDate}:not-a-uuid",
            )) assertEquals(
            "invalid_page",
            (parseAttendanceExceptionCursor(company, from, until, null, token) as Result.Failed)
                .failure
                .code,
        )
    }

    @ParameterizedTest
    @CsvSource(
        "false,false,false,false",
        "true,false,false,true",
        "false,true,false,true",
        "true,true,false,false",
        "false,false,true,false",
        "true,false,true,false",
        "false,true,true,false",
        "true,true,true,false",
    )
    fun anExplicitCorrectionSupersedesOnlyTheRawPairCompleteness(
        start: Boolean,
        end: Boolean,
        corrected: Boolean,
        expected: Boolean,
    ) {
        assertEquals(expected, attendancePairIncomplete(start, end, corrected))
    }
}
