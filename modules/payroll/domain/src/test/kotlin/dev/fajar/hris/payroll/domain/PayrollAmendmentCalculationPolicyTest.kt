package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentCalculationPolicyTest : PayrollAmendmentFixture() {
    @Test
    fun `September correction updates December reconciliation without rewriting retained results`() {
        val original = sources()
        val result = preview(original, listOf(correction(original.first(), "16500000")))
        assertEquals(listOf(9, 10, 11, 12), result.calculations.map { it.facts.month.monthValue })
        // Derived from the reviewed PMK168 fixture: annual taxable income rises from 5.6m to 6.6m.
        money("330000", requireNotNull(result.calculations.last().calculation.tax.annualTax))
        money("-2995000", result.calculations.last().calculation.tax.withheld)
        money("18395000", result.calculations.last().calculation.tax.takeHome)
        money("1000000", result.difference.taxableGross)
        money("50000", result.difference.withheld)
        money("950000", result.difference.takeHome)
        money("16500000", result.calculations[1].facts.taxHistory.history.taxableGross)
        money("0", result.calculations[1].difference.takeHome)
        money("0", result.calculations[2].difference.takeHome)
        money("20000", result.calculations.last().difference.takeHome)
        money("280000", requireNotNull(original.last().calculation.tax.annualTax))
        money("-2975000", original.last().calculation.tax.withheld)
        assertSame(original.first().calculation, result.calculations.first().original)
        assertEquals(0, result.calculations.first().target.previousAssessmentRevision)
        for ((index, entry) in result.calculations.withIndex().drop(1)) {
            assertEquals(1, entry.target.previousAssessmentRevision)
            assertEquals(
                "assessment:${original[index - 1].assessmentId}:1",
                entry.facts.taxHistory.reference,
            )
            assertEquals(0, original[index].target.previousAssessmentRevision)
        }
    }

    @Test
    fun `lower entitlement remains a signed difference instead of being clamped to a transfer`() {
        val original = sources()
        val result = preview(original, listOf(correction(original.first(), "14500000")))
        money("-1000000", result.difference.taxableGross)
        money("-50000", result.difference.withheld)
        money("-950000", result.difference.takeHome)
        money("230000", requireNotNull(result.calculations.last().calculation.tax.annualTax))
        money("-2810000", result.calculations.last().calculation.tax.withheld)
        assertTrue(result.calculations.all { it.calculation.tax.takeHome.signum() >= 0 })
    }

    @Test
    fun `unchanged earlier months supply history without joining the recalculation`() {
        val original = sources()
        val result =
            preview(original, listOf(correction(original[1], "16000000")), original[1].assessmentId)
        assertEquals(4, result.sourceVersions.size)
        assertEquals(listOf(10, 11, 12), result.calculations.map { it.facts.month.monthValue })
        money("15500000", result.calculations.first().facts.taxHistory.history.taxableGross)
        money("25000", result.difference.withheld)
        money("475000", result.difference.takeHome)
        assertEquals(0, result.calculations.first().target.previousAssessmentRevision)
        assertEquals(
            "assessment:${original.first().assessmentId}",
            result.calculations.first().facts.taxHistory.reference,
        )
        assertEquals(1, result.calculations[1].target.previousAssessmentRevision)
    }

    @Test
    fun `a second amendment uses current retained amounts and preserves the unchanged predecessor revision`() {
        val original = sources()
        val first = preview(original, listOf(correction(original.first(), "16500000")))
        val published =
            original.zip(first.calculations) { source, result ->
                source.copy(
                    revision = 1,
                    target = result.target,
                    facts = result.facts,
                    calculation = result.calculation,
                )
            }
        val second =
            preview(
                published,
                listOf(correction(published[1], "16000000")),
                published[1].assessmentId,
            )
        assertEquals(List(4) { 1L }, second.sourceVersions.map { it.revision })
        assertEquals(
            listOf(1L, 2L, 2L),
            second.calculations.map { it.target.previousAssessmentRevision },
        )
        assertEquals(
            "assessment:${published[0].assessmentId}:1",
            second.calculations[0].facts.taxHistory.reference,
        )
        assertEquals(
            "assessment:${published[1].assessmentId}:2",
            second.calculations[1].facts.taxHistory.reference,
        )
        assertSame(published[1].calculation, second.calculations[0].original)
        money("16500000", second.calculations[0].facts.taxHistory.history.taxableGross)
        money("500000", second.difference.taxableGross)
        money("25000", second.difference.withheld)
        money("475000", second.difference.takeHome)
    }

    @Test
    fun `recalculation never substitutes a different predecessor or an unobserved revision`() {
        val original = sources()
        for ((target, code) in
            listOf(
                original[1].target.copy(previousAssessmentId = UUID.randomUUID()) to
                    "payroll_tax_assessment_missing",
                original[1].target.copy(previousAssessmentRevision = 4) to
                    "payroll_tax_history_changed",
            )) {
            val changed = original.toMutableList().also { it[1] = it[1].copy(target = target) }
            val result =
                recalculatePayrollAmendment(
                    original.first().assessmentId,
                    changed,
                    listOf(correction(original.first(), "16000000")),
                )
            assertEquals(code, (result as Result.Failed).failure.code)
        }
        val orphan =
            original.toMutableList().also {
                it[0] = it[0].copy(target = it[0].target.copy(previousAssessmentRevision = 2))
            }
        val result =
            recalculatePayrollAmendment(
                original.first().assessmentId,
                orphan,
                listOf(correction(original.first(), "16000000")),
            )
        assertEquals("payroll_tax_history_changed", (result as Result.Failed).failure.code)
    }

    @Test
    fun `multiple corrections are applied in source chronology regardless of input order`() {
        val original = sources()
        val changes =
            listOf(correction(original[0], "16500000"), correction(original[1], "16000000"))
        val forward = preview(original, changes)
        val reverse = preview(original, changes.reversed())
        assertEquals(forward, reverse)
        money("355000", requireNotNull(forward.calculations.last().calculation.tax.annualTax))
        money("1425000", forward.difference.takeHome)
    }

    @Test
    fun `a full year is bounded and includes every later assessment`() {
        val original = sources(1..12, "3000000")
        val result = preview(original, listOf(correction(original.first(), "4000000")))
        assertEquals(12, result.calculations.size)
        money("34000000", result.calculations.last().facts.taxHistory.history.taxableGross)
        money("1000000", result.difference.takeHome)
    }

    @Test
    fun `gross up and previous employer credits retain their original tax treatment`() {
        val original =
            sources(
                salary = "10000000",
                treatment = TaxTreatment.GROSS_UP,
                previousEmployerNet = "25000000",
                previousEmployerWithheld = "750000",
            )
        val result = preview(original, listOf(correction(original.first(), "11000000")))
        assertTrue(result.calculations.first().calculation.tax.taxAllowance.signum() > 0)
        for (entry in result.calculations) {
            money("25000000", entry.facts.taxHistory.history.previousEmployerNet)
            money("750000", entry.facts.taxHistory.history.previousEmployerWithheld)
            assertEquals(TaxTreatment.GROSS_UP, entry.facts.compensation.treatment)
        }
    }

    @Test
    fun `existing calculation failures retain classification and identify the affected assessment`() {
        val original = sources()
        val first = original.first()
        val correction =
            PayrollAmendmentCorrection(
                first.assessmentId,
                first.facts.copy(compensation = first.facts.compensation.copy(payBasis = null)),
            )
        val result = recalculatePayrollAmendment(first.assessmentId, original, listOf(correction))
        assertTrue(result is Result.Failed)
        val error = (result as Result.Failed).failure
        assertEquals(FailureKind.CONFLICT, error.kind)
        assertEquals("payroll_pay_basis_required", error.code)
        assertEquals(first.assessmentId.toString(), error.parameters["assessmentId"])
        assertEquals("2024-09", error.parameters["taxMonth"])
    }

    @Test
    fun `unknown duplicated earlier and empty corrections cannot change another assessment`() {
        val original = sources()
        val first = original[1]
        val change = correction(first, "16000000")
        for (changes in
            listOf(
                emptyList(),
                listOf(change, change),
                listOf(change.copy(assessmentId = UUID.randomUUID())),
                listOf(correction(original.first(), "16000000")),
            )) {
            val result = recalculatePayrollAmendment(first.assessmentId, original, changes)
            assertEquals(
                "invalid_payroll_amendment_corrections",
                (result as Result.Failed).failure.code,
            )
        }
        val unknown = recalculatePayrollAmendment(UUID.randomUUID(), original, listOf(change))
        assertEquals("payroll_assessment_not_found", (unknown as Result.Failed).failure.code)
    }

    @Test
    fun `mixed scope unsorted duplicates and overflowing versions are rejected before calculation`() {
        val original = sources()
        val first = original.first()
        for (values in
            listOf(
                emptyList(),
                original.reversed(),
                original + original,
                listOf(first, first) + original.drop(2),
                listOf(first.copy(companyId = UUID.randomUUID())) + original.drop(1),
                listOf(first.copy(personId = UUID.randomUUID())) + original.drop(1),
                listOf(first.copy(revision = -1)) + original.drop(1),
                listOf(first.copy(revision = Long.MAX_VALUE)) + original.drop(1),
                listOf(first.copy(revision = Long.MAX_VALUE - 1)) + original.drop(1),
                listOf(
                    first.copy(opening = first.opening.copy(status = PayrollTaxOpeningStatus.DRAFT))
                ) + original.drop(1),
            )) {
            val result =
                recalculatePayrollAmendment(
                    first.assessmentId,
                    values,
                    listOf(correction(first, "16000000")),
                )
            assertEquals(
                "payroll_amendment_sources_invalid",
                (result as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun `corrections cannot move a published assessment into a different tax month`() {
        val original = sources()
        val first = original.first()
        val correction =
            PayrollAmendmentCorrection(
                first.assessmentId,
                first.facts.copy(month = first.facts.month.plusMonths(1)),
            )
        val result = recalculatePayrollAmendment(first.assessmentId, original, listOf(correction))
        assertEquals("payroll_amendment_month_changed", (result as Result.Failed).failure.code)
    }

    @Test
    fun `missing predecessor evidence remains a tax continuity failure`() {
        val original = sources()
        val selected = original.drop(1)
        val result =
            recalculatePayrollAmendment(
                selected.first().assessmentId,
                selected,
                listOf(correction(selected.first(), "16000000")),
            )
        assertEquals("payroll_tax_assessment_missing", (result as Result.Failed).failure.code)
    }

    @Test
    fun `cancellation propagates before and during calculation and the calling thread remains reusable`() {
        val original = sources()
        val first = original.first()
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                recalculatePayrollAmendment(
                    first.assessmentId,
                    original,
                    listOf(correction(first, "16000000")),
                )
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        val days =
            object : AbstractList<PayrollWorkDay>() {
                override val size = first.facts.workDays.size

                override fun get(index: Int): PayrollWorkDay {
                    if (index == 5) Thread.currentThread().interrupt()
                    return first.facts.workDays[index]
                }
            }
        try {
            assertThrows(InterruptedException::class.java) {
                recalculatePayrollAmendment(
                    first.assessmentId,
                    original,
                    listOf(
                        PayrollAmendmentCorrection(
                            first.assessmentId,
                            first.facts.copy(workDays = days),
                        )
                    ),
                )
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(4, preview(original, listOf(correction(first, "16000000"))).calculations.size)
    }
}
