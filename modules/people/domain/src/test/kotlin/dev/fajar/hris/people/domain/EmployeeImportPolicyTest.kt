package dev.fajar.hris.people.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EmployeeImportPolicyTest {
    @Test
    fun duplicateFileNumbersMarkEveryOccurrenceAndKeepParserIssues() {
        val rows =
            listOf(
                EmployeeImportRow(1, "E01", "First", null, emptyMap()),
                EmployeeImportRow(2, "E02", "Second", null, emptyMap()),
                EmployeeImportRow(3, "E01", "Third", null, mapOf("startDate" to "invalid_date")),
            )
        val result = annotateEmployeeImportDuplicates(rows)
        assertEquals("duplicate_employee_number_in_import", result[0].issues["employeeNumber"])
        assertTrue(result[1].issues.isEmpty())
        assertEquals("duplicate_employee_number_in_import", result[2].issues["employeeNumber"])
        assertEquals(rows[2].parseIssues, result[2].parseIssues)
        assertTrue(rows.all { it.issues.isEmpty() })
    }

    @Test
    fun checkpointParsingRejectsInvalidOrUnboundedPositions() {
        assertEquals(Result.Success(0), parseEmployeeImportCursor(emptyMap()))
        assertEquals(Result.Success(5000), parseEmployeeImportCursor(mapOf("afterRow" to "5000")))
        for (value in listOf("-1", "5001", "x", "999999999999999999999")) assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "invalid_import_checkpoint")),
            parseEmployeeImportCursor(mapOf("afterRow" to value)),
        )
    }
}
