package dev.fajar.hris.people.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.CommonsEmployeeCsvDataSource
import dev.fajar.hris.people.data.errors.safeCsvCall
import dev.fajar.hris.people.data.repositories.CsvEmployeeImportInputRepository
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EmployeeCsvTest {
    private val source = CommonsEmployeeCsvDataSource()
    private val repository = CsvEmployeeImportInputRepository(source)
    private val header = "employee_number,legal_name,nationality,start_date,contract"

    @Test
    fun rfc4180UnicodeBomAndQuotedNewlinesArePreservedBeforeBusinessNormalization() {
        val decoded =
            repository.decodeCsv(
                "\uFEFF$header\r\nE01,\"Alya, Putri\nNúñez\",id,2026-01-01,PERMANENT\r\n"
            )
        assertTrue(decoded is Result.Success, decoded.toString())
        val row = (decoded as Result.Success).value.single()
        assertEquals("Alya, Putri\nNúñez", row.draft?.person?.legalName)
        assertEquals("id", row.draft?.person?.nationality)
        assertEquals(1, row.number)
        assertEquals(emptyMap<String, String>(), row.parseIssues)
        assertTrue((repository.template() as Result.Success).value.startsWith(header))
    }

    @Test
    fun parserRejectsAmbiguousHeadersMalformedRecordsAndBoundedInputViolations() {
        for (csv in
            listOf(
                "employee_number,employee_number\nE01,E02",
                "$header,account_id\nE01,A,ID,2026-01-01,PERMANENT,",
                "$header\nE01,A,ID",
                "$header\nE01,\"unfinished",
                "$header\nE01,${"x".repeat(1025)},ID,2026-01-01,PERMANENT",
                header + "\n" + "E01,A,ID,2026-01-01,PERMANENT\n".repeat(5001),
                "x".repeat(524289),
                "$header\nE01,A\u0000B,ID,2026-01-01,PERMANENT",
            )) assertEquals(
            Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employee_csv")),
            repository.decodeCsv(csv),
        )
    }

    @Test
    fun invalidFieldTypesRemainRowIssuesWithoutInventingDatesOrIds() {
        val decoded =
            repository.decodeCsv(
                "$header,birth_date,manager_id\nE01,A,ID,not-a-date,OTHER,2026-13-01,1-1-1-1-1"
            )
        assertTrue(decoded is Result.Success, decoded.toString())
        val row = (decoded as Result.Success).value.single()
        assertNull(row.draft)
        assertEquals(setOf("startDate", "birthDate", "managerId", "contract"), row.parseIssues.keys)
        assertFalse(row.parseIssues.toString().contains("not-a-date"))
    }

    @Test
    fun cancellationAndLateInterruptionArePropagatedAndUnexpectedMessagesStayOutOfFailures() {
        assertThrows(CancellationException::class.java) {
            safeCsvCall<String> { throw CancellationException() }
        }
        try {
            assertThrows(InterruptedException::class.java) {
                safeCsvCall {
                    Thread.currentThread().interrupt()
                    "completed"
                }
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(
            Result.Failed(Failure(FailureKind.UNEXPECTED, "employee_csv_failure")),
            safeCsvCall<String> { throw IllegalStateException("private employee value") },
        )
        assertEquals(Result.Success("ready"), safeCsvCall { "ready" })
    }
}
