package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentAttendancePolicyTest : PayrollAmendmentFixture() {
    private fun attendance(source: PayrollAmendmentSource): PayrollAmendmentAttendance {
        val id = UUID.randomUUID()
        return PayrollAmendmentAttendance(
            source.companyId,
            source.assessmentId,
            source.target.employeeId,
            source.workJobId,
            source.facts.month,
            PayrollWorkAdjustment(
                id,
                1,
                source.facts.month.atDay(1),
                Instant.parse("2026-10-01T00:00:00Z"),
                "Reviewed attendance",
            ),
            id,
            source.facts.workDays.mapIndexed { index, day ->
                if (index == 0) day.copy(attendance = PayrollAttendanceKind.WORKED) else day
            },
        )
    }

    @Test
    fun explicitAttendanceAloneIsAValidProposalAndChangesOnlyItsOwnedWorkFacts() {
        val year = sources(9..9)
        val first = year.first()
        val work = attendance(first)
        val terms =
            PayrollAmendmentTerms(
                listOf(PayrollAmendmentSourceReference(first.assessmentId, 0)),
                listOf(
                    PayrollAmendmentRemuneration(
                        first.assessmentId,
                        workAdjustmentId = work.adjustment.id,
                    )
                ),
            )
        assertEquals(
            Result.Success(Unit),
            validatePayrollAmendmentProposal(first.assessmentId, terms),
        )
        val result =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                year,
                terms.sourceVersions,
                terms.corrections,
                listOf(work),
            )
        val facts = (result as Result.Success).value.single().facts
        assertEquals(work.adjustment.id, facts.workAdjustmentId)
        assertSame(work.days, facts.workDays)
        assertEquals(
            first.facts,
            facts.copy(
                workDays = first.facts.workDays,
                workAdjustmentId = first.facts.workAdjustmentId,
            ),
        )
        assertNull(first.facts.workAdjustmentId)
    }

    @Test
    fun aLaterWorkRevisionPreventsNewCalculationButCannotReplaceTheSelectedImmutableEvidence() {
        val year = sources(9..9)
        val first = year.first()
        val work = attendance(first).copy(latestAdjustmentId = UUID.randomUUID())
        val result =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                year,
                listOf(PayrollAmendmentSourceReference(first.assessmentId, 0)),
                listOf(
                    PayrollAmendmentRemuneration(
                        first.assessmentId,
                        workAdjustmentId = work.adjustment.id,
                    )
                ),
                listOf(work),
            )
        assertEquals(
            work.adjustment.id,
            (result as Result.Success).value.single().facts.workAdjustmentId,
        )
        val current = requireCurrentPayrollAmendmentAttendance(listOf(work)) as Result.Failed
        assertEquals("stale_workforce_version", current.failure.code)
        assertEquals(first.assessmentId.toString(), current.failure.parameters["assessmentId"])
    }

    @Test
    fun absentSelectionRetainsPublishedWorkEvenWhenAnotherRevisionIsAvailable() {
        val first = sources(9..9).first()
        val work = attendance(first)
        val retained =
            first.copy(
                facts = first.facts.copy(workAdjustmentId = UUID.randomUUID()),
                availableWorkAdjustment = work.adjustment,
            )
        val result =
            preparePayrollAmendmentCorrections(
                first.assessmentId,
                listOf(retained),
                listOf(PayrollAmendmentSourceReference(first.assessmentId, 0)),
                listOf(PayrollAmendmentRemuneration(first.assessmentId, input = first.facts.input)),
                listOf(work),
            )
        val facts = (result as Result.Success).value.single().facts
        assertEquals(retained.facts.workAdjustmentId, facts.workAdjustmentId)
        assertSame(first.facts.workDays, facts.workDays)
    }

    @Test
    fun anotherScopeOrMissingSnapshotCannotReplaceThePublishedSource() {
        val first = sources(9..9).first()
        val work = attendance(first)
        val variants =
            listOf(
                work.copy(companyId = UUID.randomUUID()),
                work.copy(employeeId = UUID.randomUUID()),
                work.copy(assessmentId = UUID.randomUUID()),
                work.copy(workJobId = UUID.randomUUID()),
                work.copy(month = work.month.plusMonths(1)),
                work.copy(adjustment = work.adjustment.copy(id = UUID.randomUUID())),
            )
        for (acquired in variants.map { listOf(it) } + listOf(emptyList())) {
            val result =
                preparePayrollAmendmentCorrections(
                    first.assessmentId,
                    listOf(first),
                    listOf(PayrollAmendmentSourceReference(first.assessmentId, 0)),
                    listOf(
                        PayrollAmendmentRemuneration(
                            first.assessmentId,
                            workAdjustmentId = work.adjustment.id,
                        )
                    ),
                    acquired,
                )
            assertEquals("stale_workforce_version", (result as Result.Failed).failure.code)
        }
    }

    @Test
    fun theOperationChangesWhenItsSelectedAttendanceChanges() {
        val first = sources(9..9).first()
        val work = attendance(first)
        val terms =
            PayrollAmendmentTerms(
                listOf(PayrollAmendmentSourceReference(first.assessmentId, 0)),
                listOf(
                    PayrollAmendmentRemuneration(
                        first.assessmentId,
                        workAdjustmentId = work.adjustment.id,
                    )
                ),
            )
        val replacement =
            terms.copy(
                corrections =
                    terms.corrections.map { it.copy(workAdjustmentId = UUID.randomUUID()) }
            )
        assertNotEquals(payrollAmendmentParts(terms), payrollAmendmentParts(replacement))
        assertEquals(
            mapOf(first.assessmentId to work.adjustment.id),
            payrollAmendmentAttendanceReferences(terms.corrections),
        )
    }

    @Test
    fun sourceReferencesAndNestedProposalsAreDetachedBeforeWaiting() {
        val first = sources(9..9).first()
        val references = mutableListOf(PayrollAmendmentSourceReference(first.assessmentId, 0))
        val earnings = first.facts.input.earnings.toMutableList()
        val days = first.facts.input.dayResolutions.toMutableList()
        val fixed = first.facts.compensation.fixedEarnings.toMutableList()
        val insurance = first.facts.compensation.insurancePrograms.toMutableSet()
        val corrections =
            mutableListOf(
                PayrollAmendmentRemuneration(
                    first.assessmentId,
                    first.facts.compensation.copy(
                        fixedEarnings = fixed,
                        insurancePrograms = insurance,
                    ),
                    first.facts.input.copy(earnings = earnings, dayResolutions = days),
                )
            )
        val expected = first.facts
        val snapshot = snapshotPayrollAmendmentTerms(PayrollAmendmentTerms(references, corrections))
        references.clear()
        corrections.clear()
        earnings.clear()
        days.clear()
        fixed.clear()
        insurance.clear()
        assertEquals(1, snapshot.sourceVersions.size)
        assertEquals(expected.input, snapshot.corrections.single().input)
        assertEquals(expected.compensation, snapshot.corrections.single().compensation)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.corrections as MutableList<PayrollAmendmentRemuneration>).clear()
        }
    }
}
