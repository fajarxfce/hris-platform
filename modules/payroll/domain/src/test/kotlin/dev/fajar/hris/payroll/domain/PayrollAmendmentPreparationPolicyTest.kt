package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentPreparationPolicyTest : PayrollAmendmentFixture() {
    @Test
    fun `only proposed remuneration changes while policy work employment and opening evidence remain retained`() {
        val original = sources()
        val first = original.first()
        val compensation = first.facts.compensation.copy(basicSalary = amount("16500000"))
        val input = first.facts.input.copy(reviewReference = "Reviewed amendment input")
        val result =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                original,
                original.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) },
                listOf(PayrollAmendmentRemuneration(first.assessmentId, compensation, input)),
            )
        val facts = (result as Result.Success).value.single().facts
        assertEquals(compensation, facts.compensation)
        assertEquals(input, facts.input)
        assertSame(first.facts.workDays, facts.workDays)
        assertSame(first.facts.employment, facts.employment)
        assertSame(first.facts.leaveDays, facts.leaveDays)
        assertSame(first.facts.policy, facts.policy)
        assertSame(first.facts.taxHistory, facts.taxHistory)
        assertEquals(first.facts.incomeDueDate, facts.incomeDueDate)
        assertEquals(first.facts.plannedPaymentDate, facts.plannedPaymentDate)
        money("15500000", first.facts.compensation.basicSalary)
    }

    @Test
    fun `input only corrections still use the existing monthly calculation`() {
        val original = sources()
        val first = original.first()
        val input =
            first.facts.input.copy(
                earnings =
                    listOf(
                        PayrollVariableEarning(
                            "ADJUSTMENT",
                            "Reviewed earnings",
                            amount("1000000"),
                            true,
                        )
                    )
            )
        val prepared =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                original,
                original.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) },
                listOf(PayrollAmendmentRemuneration(first.assessmentId, input = input)),
            )
        val corrections = (prepared as Result.Success).value
        assertSame(first.facts.compensation, corrections.single().facts.compensation)
        money("1000000", preview(original, corrections).difference.taxableGross)
    }

    @Test
    fun `every observed year reference matters including the unchanged prefix and newly published months`() {
        val original = sources()
        val first = original[1]
        val references =
            original.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
        val proposal =
            listOf(PayrollAmendmentRemuneration(first.assessmentId, first.facts.compensation))
        for (observed in
            listOf(
                references.drop(1),
                references.dropLast(1),
                references.mapIndexed { index, ref ->
                    if (index == 0) ref.copy(revision = 1) else ref
                },
            )) {
            val result =
                preparePayrollAmendmentCorrections(first.assessmentId, original, observed, proposal)
            assertEquals(
                "payroll_amendment_sources_changed",
                (result as Result.Failed).failure.code,
            )
        }
        val forward =
            preparePayrollAmendmentCorrections(first.assessmentId, original, references, proposal)
        val reverse =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                original,
                references.reversed(),
                proposal,
            )
        assertTrue(forward is Result.Success)
        assertEquals(forward, reverse)
    }

    @Test
    fun `prefix and foreign corrections cannot join the selected suffix`() {
        val original = sources()
        val first = original[1]
        val references =
            original.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
        val proposal = PayrollAmendmentRemuneration(first.assessmentId, first.facts.compensation)
        for (id in listOf(original.first().assessmentId, UUID.randomUUID())) {
            val result =
                preparePayrollAmendmentCorrections(
                    first.assessmentId,
                    original,
                    references,
                    listOf(proposal, proposal.copy(assessmentId = id)),
                )
            assertEquals(
                "invalid_payroll_amendment_corrections",
                (result as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun `empty repeated unbounded and invalid version inputs do not enter calculation`() {
        val original = sources()
        val first = original.first()
        val references =
            original.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
        val proposal = PayrollAmendmentRemuneration(first.assessmentId, first.facts.compensation)
        for (proposals in
            listOf(
                emptyList(),
                listOf(proposal, proposal),
                List(13) { proposal },
                listOf(PayrollAmendmentRemuneration(first.assessmentId)),
            )) {
            val result =
                preparePayrollAmendmentCorrections(
                    first.assessmentId,
                    original,
                    references,
                    proposals,
                )
            assertEquals(
                "invalid_payroll_amendment_corrections",
                (result as Result.Failed).failure.code,
            )
        }
        for (observed in
            listOf(
                emptyList(),
                references + references,
                references.map { it.copy(revision = -1) },
                references.map { it.copy(revision = Long.MAX_VALUE) },
            )) {
            val result =
                preparePayrollAmendmentCorrections(
                    first.assessmentId,
                    original,
                    observed,
                    listOf(proposal),
                )
            assertEquals(
                "invalid_payroll_amendment_corrections",
                (result as Result.Failed).failure.code,
            )
        }
        val oversized =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                List(13) { first },
                references,
                listOf(proposal),
            )
        assertEquals("payroll_amendment_sources_invalid", (oversized as Result.Failed).failure.code)
    }

    @Test
    fun `a remuneration correction cannot forge a new verified tax registration`() {
        val original = sources()
        val first = original.first()
        val compensation =
            first.facts.compensation.copy(
                tax =
                    first.facts.compensation.tax.copy(
                        verificationReference = "Unverified replacement"
                    )
            )
        val result =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                original,
                original.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) },
                listOf(PayrollAmendmentRemuneration(first.assessmentId, compensation)),
            )
        assertEquals(
            "payroll_amendment_tax_evidence_changed",
            (result as Result.Failed).failure.code,
        )
        assertEquals(first.assessmentId.toString(), result.failure.parameters["assessmentId"])
    }

    @Test
    fun `interrupted preparation propagates cancellation`() {
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                preparePayrollAmendmentCorrections(
                    UUID.randomUUID(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
