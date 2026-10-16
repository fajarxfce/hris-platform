package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentPublicationPolicyTest : PayrollAmendmentFixture() {
    private fun amendment(
        year: List<PayrollAmendmentSource>,
        preview: PayrollAmendmentPreview,
    ): PayrollAmendment {
        val first = year.first()
        val now = Instant.parse("2024-12-31T00:00:00Z")
        val actor = UUID.randomUUID()
        return PayrollAmendment(
            PayrollAmendmentSummary(
                UUID.randomUUID(),
                first.assessmentId,
                first.facts.month,
                first.personId,
                PayrollEmployee(first.target.employeeId, "E001", "Example employee", 0),
                3,
                PayrollAmendmentStatus.APPROVED,
                PayrollAmendmentAction.APPROVED,
                UUID.randomUUID(),
                now,
                actor,
                now,
                "Approved correction",
            ),
            PayrollAmendmentTerms(
                preview.sourceVersions,
                listOf(
                    PayrollAmendmentRemuneration(
                        first.assessmentId,
                        preview.calculations.first().facts.compensation,
                        null,
                    )
                ),
            ),
        )
    }

    @Test
    fun publicationRequiresTheCompleteObservedYearAndExactRetainedPredecessorRevision() {
        val year = sources(9..12)
        val proposed = preview(year, listOf(correction(year.first(), "16500000")))
        val amendment = amendment(year, proposed)
        assertEquals(
            Result.Success(Unit),
            requirePayrollAmendmentPublicationSources(amendment, proposed, year),
        )
        assertEquals(
            "payroll_amendment_sources_changed",
            (requirePayrollAmendmentPublicationSources(amendment, proposed, year.dropLast(1))
                    as Result.Failed)
                .failure
                .code,
        )
        val wrong =
            proposed.copy(
                calculations =
                    proposed.calculations.mapIndexed { i, it ->
                        if (i == 1) it.copy(target = it.target.copy(previousAssessmentRevision = 0))
                        else it
                    }
            )
        assertEquals(
            "payroll_amendment_recalculation_required",
            (requirePayrollAmendmentPublicationSources(amendment, wrong, year) as Result.Failed)
                .failure
                .code,
        )
        val changed = year.mapIndexed { i, it -> if (i == 0) it.copy(revision = 1) else it }
        val forged =
            proposed.copy(
                calculations =
                    proposed.calculations.mapIndexed { i, it ->
                        if (i == 0)
                            it.copy(facts = it.facts.copy(workAdjustmentId = UUID.randomUUID()))
                        else it
                    }
            )
        assertEquals(
            "payroll_amendment_recalculation_required",
            (requirePayrollAmendmentPublicationSources(amendment, forged, year) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "payroll_amendment_sources_changed",
            (requirePayrollAmendmentPublicationSources(amendment, proposed, changed)
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun boundedRetriesReserveBothAdmissionAndItsTerminalOutcome() {
        val year = sources(9..9)
        val preview = preview(year, listOf(correction(year.first(), "16500000")))
        val current = amendment(year, preview).summary
        val prior =
            PayrollAmendmentPublication(
                UUID.randomUUID(),
                current.id,
                UUID.randomUUID(),
                current.latestCalculationId!!,
                3,
                1,
                1,
                1,
                UUID.randomUUID(),
                current.actorId,
                current.recordedAt,
                "Approved correction",
                PayrollAmendmentPublicationStatus.STOPPED,
                current.recordedAt,
                "job_failed",
                current.actorId,
            )
        assertEquals(Result.Success(1), nextPayrollAmendmentPublication(current, null))
        assertEquals(Result.Success(2), nextPayrollAmendmentPublication(current, prior))
        assertEquals(
            Result.Success(2),
            nextPayrollAmendmentPublication(current.copy(version = 996), prior),
        )
        assertEquals(
            "payroll_amendment_revision_limit",
            (nextPayrollAmendmentPublication(current.copy(version = 997), prior) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "payroll_amendment_publication_limit",
            (nextPayrollAmendmentPublication(current, prior.copy(number = 8)) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "payroll_amendment_publication_active",
            (nextPayrollAmendmentPublication(
                    current,
                    prior.copy(status = PayrollAmendmentPublicationStatus.PENDING),
                )
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun interruptedPublicationValidationPropagatesCancellation() {
        val year = sources(9..9)
        val proposed = preview(year, listOf(correction(year.first(), "16500000")))
        val current = amendment(year, proposed)
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                requirePayrollAmendmentPublicationSources(current, proposed, year)
            }
        } finally {
            Thread.interrupted()
        }
    }
}
