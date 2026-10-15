package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentDraftPolicyTest : PayrollAmendmentFixture() {
    @Test
    fun commandIdentityIgnoresReferenceOrderingButRetainsEveryProposedValueAndObservedVersion() {
        val year = sources(9..10)
        val terms =
            PayrollAmendmentTerms(
                year.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) },
                year.map {
                    PayrollAmendmentRemuneration(
                        it.assessmentId,
                        it.facts.compensation,
                        it.facts.input,
                    )
                },
            )
        assertEquals(
            Result.Success(Unit),
            validatePayrollAmendmentDraft(
                year.first().assessmentId,
                terms,
                null,
                "Reviewed remuneration",
            ),
        )
        assertEquals(
            payrollAmendmentParts(terms),
            payrollAmendmentParts(
                terms.copy(
                    sourceVersions = terms.sourceVersions.reversed(),
                    corrections = terms.corrections.reversed(),
                )
            ),
        )
        assertNotEquals(
            payrollAmendmentParts(terms),
            payrollAmendmentParts(
                terms.copy(sourceVersions = terms.sourceVersions.map { it.copy(revision = 1) })
            ),
        )
        assertNotEquals(
            payrollAmendmentParts(terms),
            payrollAmendmentParts(
                terms.copy(corrections = terms.corrections.map { it.copy(input = null) })
            ),
        )
        assertNotEquals(
            payrollAmendmentParts(terms),
            payrollAmendmentParts(
                terms.copy(
                    corrections =
                        terms.corrections.map {
                            it.copy(
                                compensation =
                                    it.compensation!!.copy(basicSalary = BigDecimal("16000000"))
                            )
                        }
                )
            ),
        )
    }

    @Test
    fun hugeDecimalExponentsAndUnboundedProposalCollectionsAreRejectedBeforeCanonicalization() {
        val source = sources(9..9).single()
        val terms =
            PayrollAmendmentTerms(
                listOf(PayrollAmendmentSourceReference(source.assessmentId, 0)),
                listOf(PayrollAmendmentRemuneration(source.assessmentId, source.facts.compensation)),
            )
        for (amount in
            listOf(
                BigDecimal("1E+1000000000"),
                BigDecimal("1E-1000000000"),
                BigDecimal("50000000000.01"),
            )) {
            val invalid =
                terms.copy(
                    corrections =
                        listOf(
                            terms.corrections
                                .single()
                                .copy(
                                    compensation =
                                        source.facts.compensation.copy(basicSalary = amount)
                                )
                        )
                )
            val result =
                validatePayrollAmendmentDraft(
                    source.assessmentId,
                    invalid,
                    null,
                    "Reviewed remuneration",
                )
            assertEquals("invalid_payroll_amendment", (result as Result.Failed).failure.code)
        }
        for (invalid in
            listOf(
                terms.copy(sourceVersions = List(13) { terms.sourceVersions.single() }),
                terms.copy(corrections = List(13) { terms.corrections.single() }),
                terms.copy(
                    corrections = listOf(terms.corrections.single().copy(compensation = null))
                ),
            )) {
            assertEquals(
                "invalid_payroll_amendment_corrections",
                (validatePayrollAmendmentDraft(
                        source.assessmentId,
                        invalid,
                        null,
                        "Reviewed remuneration",
                    )
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
    }

    @Test
    fun proposalMustIncludeTheSelectedAssessmentAndUniqueExplicitReferences() {
        val year = sources(9..10)
        val first = year.first().assessmentId
        val terms =
            PayrollAmendmentTerms(
                year.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) },
                year.map { PayrollAmendmentRemuneration(it.assessmentId, it.facts.compensation) },
            )
        for (invalid in
            listOf(
                terms.copy(sourceVersions = terms.sourceVersions.drop(1)),
                terms.copy(corrections = terms.corrections.drop(1)),
                terms.copy(
                    sourceVersions =
                        listOf(terms.sourceVersions.first(), terms.sourceVersions.first())
                ),
                terms.copy(
                    corrections = listOf(terms.corrections.first(), terms.corrections.first())
                ),
                terms.copy(sourceVersions = terms.sourceVersions.map { it.copy(revision = -1) }),
            )) assertEquals(
            "invalid_payroll_amendment_corrections",
            (validatePayrollAmendmentDraft(first, invalid, null, "Reviewed remuneration")
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun referenceDatesComeFromEachAffectedMonthAndTaxEvidenceIsNotReplacedByTheDraft() {
        val year = sources(9..10)
        val proposals =
            year.map { PayrollAmendmentRemuneration(it.assessmentId, it.facts.compensation) }
        val corrected =
            preparePayrollAmendmentCorrections(
                year.first().assessmentId,
                year,
                year.map { PayrollAmendmentSourceReference(it.assessmentId, 0) },
                proposals,
            )
        assertTrue(corrected is Result.Success, corrected.toString())
        assertEquals(
            Result.Success(Unit),
            validatePayrollAmendmentRemuneration(
                (corrected as Result.Success).value,
                "Reviewed remuneration",
            ),
        )
        val invalid =
            corrected.value.map {
                it.copy(
                    facts =
                        it.facts.copy(
                            compensation =
                                it.facts.compensation.copy(
                                    tax =
                                        it.facts.compensation.tax.copy(
                                            subjectiveFrom = it.facts.month.plusMonths(1).atDay(1)
                                        )
                                )
                        )
                )
            }
        assertEquals(
            "invalid_compensation",
            (validatePayrollAmendmentRemuneration(invalid, "Reviewed remuneration")
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun cancelledOrExhaustedDraftsDoNotAdvertiseUnavailableCommands() {
        val source = sources(9..9).single()
        val now = Instant.parse("2024-09-30T10:00:00Z")
        val actor =
            Actor(
                UUID.randomUUID(),
                source.companyId,
                setOf("payroll.amend"),
                now,
                UUID.randomUUID(),
            )
        val summary =
            PayrollAmendmentSummary(
                UUID.randomUUID(),
                source.assessmentId,
                source.facts.month,
                source.personId,
                PayrollEmployee(
                    source.target.employeeId,
                    source.target.employeeNumber,
                    source.target.employeeName,
                    0,
                ),
                0,
                PayrollAmendmentStatus.DRAFT,
                PayrollAmendmentAction.SAVED,
                null,
                now,
                actor.accountId,
                now,
                "Reviewed remuneration",
            )
        assertEquals(
            setOf(
                PayrollAmendmentAvailableAction.EDIT,
                PayrollAmendmentAvailableAction.CALCULATE,
                PayrollAmendmentAvailableAction.CANCEL,
            ),
            availablePayrollAmendmentActions(actor, summary, null, null),
        )
        assertEquals(
            setOf(PayrollAmendmentAvailableAction.EDIT, PayrollAmendmentAvailableAction.CANCEL),
            availablePayrollAmendmentActions(
                actor,
                summary.copy(status = PayrollAmendmentStatus.CALCULATED),
                null,
                null,
            ),
        )
        assertEquals(
            emptySet<PayrollAmendmentAvailableAction>(),
            availablePayrollAmendmentActions(
                actor,
                summary.copy(status = PayrollAmendmentStatus.CANCELLED),
                null,
                null,
            ),
        )
        assertEquals(
            setOf(PayrollAmendmentAvailableAction.CANCEL),
            availablePayrollAmendmentActions(actor, summary.copy(version = 998), null, null),
        )
        assertEquals(
            emptySet<PayrollAmendmentAvailableAction>(),
            availablePayrollAmendmentActions(
                actor.copy(permissions = emptySet()),
                summary,
                null,
                null,
            ),
        )
        assertEquals(
            emptySet<PayrollAmendmentAvailableAction>(),
            availablePayrollAmendmentActions(actor, summary, actor.accountId, null),
        )
        val latest =
            PayrollAmendmentCalculationSummary(
                UUID.randomUUID(),
                summary.id,
                32,
                31,
                PayrollAmendmentCalculationStatus.FAILED,
                0,
                null,
                Failure(FailureKind.CONFLICT, "payroll_pay_basis_required"),
                actor.accountId,
                now,
                "Reviewed remuneration",
            )
        assertEquals(
            setOf(PayrollAmendmentAvailableAction.EDIT, PayrollAmendmentAvailableAction.CANCEL),
            availablePayrollAmendmentActions(actor, summary, null, latest),
        )
    }

    @Test
    fun commandsRequireBoundedVersionsAndAReviewReason() {
        for (reason in listOf("", " ", "x".repeat(1001), "Hidden\ntext")) {
            val failed = validatePayrollAmendmentCommand(0, reason)
            assertEquals(mapOf("reason" to "invalid"), (failed as Result.Failed).failure.fields)
        }
        for (version in listOf(-1L, 1000L, Long.MAX_VALUE)) assertEquals(
            "invalid_version",
            (validatePayrollAmendmentCommand(version, "Reviewed") as Result.Failed).failure.code,
        )
        assertEquals(Result.Success(Unit), validatePayrollAmendmentCommand(null, "Reviewed"))
    }

    @Test
    fun cancellationIsPropagatedBeforeValidatingOrCanonicalizingACommand() {
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                validatePayrollAmendmentCommand(0, "Reviewed")
            }
        } finally {
            Thread.interrupted()
        }
    }
}
