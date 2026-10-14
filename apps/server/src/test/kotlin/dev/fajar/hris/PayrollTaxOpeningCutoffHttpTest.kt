package dev.fajar.hris

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollTaxOpeningCutoffHttpTest : PayrollRunApiFixture() {
    @Test
    fun aCapturedTaxYearRemovesOpeningActionsWithoutDiscardingItsEvidence() {
        val scene = calculationFixture()
        val f = scene.people
        val path =
            "/api/v1/companies/${f.payroll.company}/payroll/employees/${f.payroll.employee}/tax-openings/2026/review"
        val before = payrollBody(get(f.preparer.client, path))
        assertFalse(before["frozen"].asBoolean())
        assertEquals(
            listOf("PREPARE"),
            before["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        payrollBody(createRun(scene))
        val after = payrollBody(get(f.preparer.client, path))
        assertTrue(after["frozen"].asBoolean())
        assertEquals(0, after["availableActions"].size())
        assertEquals(before["current"], after["current"])
        assertEquals(before["history"], after["history"])
    }
}
