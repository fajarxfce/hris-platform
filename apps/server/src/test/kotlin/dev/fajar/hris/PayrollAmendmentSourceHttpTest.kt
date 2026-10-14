package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.dao.TransientDataAccessResourceException

class PayrollAmendmentSourceHttpTest : PayrollAmendmentApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["malformed", "company", "month", "opening", "bound"])
    fun malformedOrMisScopedStoredSourcesAreMappedInsideTheDatabaseBoundary(kind: String) {
        val f = amendmentFixture()
        val original = amendmentPreparation(f)
        amendmentProbe.transform = { company, rows ->
            if (company != f.payroll.company) rows
            else
                when (kind) {
                    "malformed" -> rows.map { it.copy(facts = "{private-payroll-sentinel") }
                    "company" -> rows.map { it.copy(companyId = UUID.randomUUID()) }
                    "month" -> rows.map { it.copy(taxMonth = it.taxMonth.plusMonths(1)) }
                    "opening" ->
                        rows.map {
                            it.copy(opening = it.opening.copy(employeeId = UUID.randomUUID()))
                        }
                    "bound" -> List(13) { rows.first() }
                    else -> error("Unknown corruption")
                }
        }
        for (response in
            listOf(
                get(f.maker.client, amendmentPath(f) + "/amendment-preparation"),
                amendmentPreview(f, amendmentPreviewBody(original)),
            )) {
            payrollError(response, 500, "database_failure")
            assertFalse(response.body().contains("private-payroll-sentinel"))
            assertFalse(response.body().contains("IllegalArgumentException"))
        }
        amendmentProbe.clear()
        assertEquals(original, amendmentPreparation(f))
        payrollBody(amendmentPreview(f, amendmentPreviewBody(original)))
    }

    @Test
    fun technicalAvailabilityFailuresRetainTheirClassificationWithoutLeakingDriverText() {
        val f = amendmentFixture()
        val preparation = amendmentPreparation(f)
        amendmentProbe.transform = { company, rows ->
            if (company == f.payroll.company)
                throw TransientDataAccessResourceException("private-driver-sentinel")
            rows
        }
        val response = amendmentPreview(f, amendmentPreviewBody(preparation))
        payrollError(response, 503, "database_busy")
        assertFalse(response.body().contains("private-driver-sentinel"))
        amendmentProbe.clear()
        payrollBody(amendmentPreview(f, amendmentPreviewBody(preparation)))
    }
}
