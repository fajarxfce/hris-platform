package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HeadcountPolicyTest {
    @Test
    fun groupSelectionIsBoundedDistinctAndCopiedInStableOrder() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        for (selection in
            listOf(emptyList(), listOf(first, first), List(33) { UUID.randomUUID() })) {
            val result = validateHeadcountCompanies(selection) as Result.Failed
            assertEquals("invalid_report_companies", result.failure.code)
        }
        val selected = mutableListOf(second, first)
        val result = validateHeadcountCompanies(selected) as Result.Success
        selected.clear()
        assertEquals(listOf(first, second).sorted(), result.value)
        val maximum = List(32) { UUID.randomUUID() }
        assertEquals(Result.Success(maximum.sorted()), validateHeadcountCompanies(maximum))
    }

    @Test
    fun reportAccessRequiresBothReportAndCompanyWideSourcePermission() {
        val actor =
            Actor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                emptySet(),
                Instant.now(),
                UUID.randomUUID(),
            )
        for (permissions in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("people.read"),
                setOf("reports.read", "people.team.read", "people.self.read"),
            )) {
            val failure =
                validateHeadcountAccess(actor.copy(permissions = permissions)) as Result.Failed
            assertEquals("access_denied", failure.failure.code)
        }
        assertInstanceOf(
            Result.Success::class.java,
            validateHeadcountAccess(actor.copy(permissions = setOf("reports.read", "people.read"))),
        )
    }

    @Test
    fun reportDatesHaveExplicitFiniteBoundsIncludingScheduledProjections() {
        for (date in listOf("1900-01-01", "2026-10-01", "2100-12-31")) assertInstanceOf(
            Result.Success::class.java,
            validateHeadcountDate(LocalDate.parse(date)),
        )
        for (date in listOf("1899-12-31", "2101-01-01")) assertEquals(
            "invalid_report_date",
            (validateHeadcountDate(LocalDate.parse(date)) as Result.Failed).failure.code,
        )
    }
}
