package dev.fajar.hris.people.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.ReportingAssignment
import dev.fajar.hris.people.domain.policies.validateReportingLine
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportingLinePolicyTest {
    @Test
    fun futureBoundaryCannotIntroduceAReportingCycle() {
        val employee = UUID.randomUUID()
        val manager = UUID.randomUUID()
        val change = ReportingAssignment(employee, manager, LocalDate.parse("2026-05-01"), 1)
        val scheduled = ReportingAssignment(manager, employee, LocalDate.parse("2026-06-01"), 1)
        val result = validateReportingLine(change, listOf(scheduled))
        assertEquals("reporting_cycle_or_depth", (result as Result.Failed).failure.code)
    }

    @Test
    fun latestRevisionAtTheSameEffectiveDateWins() {
        val employee = UUID.randomUUID()
        val manager = UUID.randomUUID()
        val date = LocalDate.parse("2026-05-01")
        val old = ReportingAssignment(manager, employee, date, 1)
        val corrected = ReportingAssignment(manager, null, date, 2)
        assertTrue(
            validateReportingLine(
                ReportingAssignment(employee, manager, date, 1),
                listOf(old, corrected),
            )
                is Result.Success
        )
    }

    @Test
    fun anAssignmentBeforeTheChangeDoesNotCreateAFalseCycle() {
        val employee = UUID.randomUUID()
        val manager = UUID.randomUUID()
        val january = LocalDate.parse("2026-01-01")
        val april = LocalDate.parse("2026-04-01")
        val old = ReportingAssignment(manager, employee, january, 0)
        val released = ReportingAssignment(manager, null, april, 1)
        assertTrue(
            validateReportingLine(
                ReportingAssignment(employee, manager, april, 1),
                listOf(old, released),
            )
                is Result.Success
        )
    }
}
