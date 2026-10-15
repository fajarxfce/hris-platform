package dev.fajar.hris

import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentReviewHttpTest : PayrollAmendmentReviewApiFixture() {
    @Test
    fun stagedDecisionsPinCalculationAndDraftVersionsWhileOriginalPayrollRemainsImmutable() {
        val scene = amendmentReviewScene(stages = 2)
        val draft = scene.draft
        val f = draft.source
        val original = payrollBody(get(f.payroll.owner.client, amendmentPath(f)))
        val originalCounts =
            listOf(
                    "payroll_assessments",
                    "payroll_run_results",
                    "payroll_payment_batches",
                    "background_jobs",
                )
                .associateWith { count(f.payroll.company, it) }
        val first = amendmentReviewView(scene)
        assertEquals("PENDING", first["review"]["status"].asString())
        assertEquals(1, first["review"]["amendmentVersion"].asLong())
        assertEquals(scene.calculationId.toString(), first["calculation"]["id"].asString())
        assertEquals(
            setOf("APPROVE", "REJECT"),
            first["availableActions"].toList().map { it.asString() }.toSet(),
        )
        assertEquals(
            setOf("WITHDRAW"),
            amendmentReviewView(scene, f.maker)["availableActions"]
                .toList()
                .map { it.asString() }
                .toSet(),
        )
        val pending = draftView(draft)
        assertEquals("IN_REVIEW", pending["amendment"]["summary"]["status"].asString())
        assertEquals(scene.id.toString(), pending["latestReview"]["id"].asString())
        assertEquals(0, pending["availableActions"].size())
        payrollError(draftCommand(draft, "cancel", 2), 409, "payroll_amendment_not_editable")
        payrollError(draftCommand(draft, "calculate", 2), 409, "payroll_amendment_not_editable")
        payrollError(
            saveDraft(f, draft.id, draftBody(f, amendmentPreparation(f), 2)),
            409,
            "payroll_amendment_not_editable",
        )
        val firstKey = UUID.randomUUID()
        val receipt = payrollBody(decideAmendmentReview(scene, key = firstKey))
        assertEquals(draft.id.toString(), receipt["id"].asString())
        assertEquals(3, receipt["version"].asLong())
        assertEquals("PENDING", amendmentReviewView(scene)["review"]["status"].asString())
        payrollError(
            decideAmendmentReview(scene, version = 3, reviewVersion = 1, approvalVersion = 1),
            403,
            "not_assigned_approver",
        )
        payrollBody(
            decideAmendmentReview(
                scene,
                version = 3,
                reviewVersion = 1,
                approvalVersion = 1,
                member = scene.secondReviewer,
            )
        )
        val approved = amendmentReviewView(scene)
        assertEquals("APPROVED", approved["review"]["status"].asString())
        assertEquals("APPROVED", approved["amendment"]["status"].asString())
        assertEquals(
            listOf(2L, 3L, 4L),
            approved["history"].toList().map { it["amendmentRevision"].asLong() },
        )
        val beforeReplay = amendmentReviewCounts(scene)
        assertEquals(receipt, payrollBody(decideAmendmentReview(scene, key = firstKey)))
        assertEquals(2, payrollBody(submitAmendmentReview(scene))["version"].asLong())
        assertEquals(beforeReplay, amendmentReviewCounts(scene))
        payrollBody(withdrawAmendmentReview(scene, 4, 2, 2))
        val second = scene.copy(id = UUID.randomUUID(), operation = UUID.randomUUID())
        payrollBody(submitAmendmentReview(second, version = 5))
        // A prior decision alone does not make that reviewer a preparer of a later submission.
        assertTrue(
            amendmentReviewView(second)["availableActions"].toList().any {
                it.asString() == "APPROVE"
            }
        )
        payrollBody(decideAmendmentReview(second, version = 6))
        val retained = amendmentReviewView(scene)
        assertEquals("WITHDRAWN", retained["review"]["status"].asString())
        assertEquals(0, retained["availableActions"].size())
        val list = payrollBody(get(f.maker.client, amendmentReviewsPath(scene)))
        assertEquals(listOf(1, 2), list.toList().map { it["number"].asInt() })
        assertEquals(original, payrollBody(get(f.payroll.owner.client, amendmentPath(f))))
        assertEquals(
            originalCounts,
            originalCounts.keys.associateWith { count(f.payroll.company, it) },
        )
    }

    @Test
    fun rejectionRequiresAnExplicitEditAndFreshCalculationBeforeAnotherReview() {
        val scene = amendmentReviewScene()
        payrollBody(decideAmendmentReview(scene, decision = "REJECT"))
        val f = scene.draft.source
        val rejected = draftView(scene.draft)
        assertEquals("REJECTED", rejected["amendment"]["summary"]["status"].asString())
        assertEquals(
            setOf("EDIT", "CANCEL"),
            rejected["availableActions"].toList().map { it.asString() }.toSet(),
        )
        val next = scene.copy(id = UUID.randomUUID(), operation = UUID.randomUUID())
        payrollError(
            submitAmendmentReview(next, version = 3),
            409,
            "payroll_amendment_calculation_required",
        )
        payrollBody(saveDraft(f, scene.draft.id, draftBody(f, amendmentPreparation(f), 3)))
        payrollBody(draftCommand(scene.draft, "calculate", 4))
        val recalculated =
            UUID.fromString(draftView(scene.draft)["latestCalculation"]["id"].asString())
        assertNotEquals(scene.calculationId, recalculated)
        payrollBody(submitAmendmentReview(next.copy(calculationId = recalculated), version = 5))
        assertEquals("REJECTED", amendmentReviewView(scene)["review"]["status"].asString())
    }

    @Test
    fun exposureUsesTheRetainedPerMonthDifferencesAndSelectsTheEffectiveApprovalThreshold() {
        val scene = amendmentReviewScene(submitted = false)
        val high =
            payrollMember(
                scene.draft.source.payroll.company,
                setOf("company.read", "payroll.amend.review"),
            )
        val template =
            amendmentReviewTemplate(
                scene.draft,
                listOf(high.account),
                changes = mapOf("minimumAmount" to "1000000"),
            )
        payrollBody(submitAmendmentReview(scene))
        val view = amendmentReviewView(scene)
        assertEquals(template.toString(), view["approval"]["templateId"].asString())
        assertEquals(
            0,
            BigDecimal(view["review"]["exposure"].asString()).compareTo(BigDecimal("1000000")),
        )
        assertEquals("2026-10-01", view["review"]["referenceDate"].asString())
        payrollError(decideAmendmentReview(scene), 403, "not_assigned_approver")
        payrollBody(decideAmendmentReview(scene, member = high))
    }

    @Test
    fun anAdditionalPublishedMonthBlocksApprovalAndSubmissionButPermitsRejectionWithdrawalAndReceiptRecovery() {
        val submitted = amendmentReviewScene()
        val waiting =
            amendmentReviewScene(
                draftFixture(submitted.draft.source),
                submitted = false,
                configureTemplate = false,
            )
        val rejecting =
            amendmentReviewScene(draftFixture(submitted.draft.source), configureTemplate = false)
        val next =
            followingMonth(submitted.draft.source.publication.calculation, YearMonth.of(2026, 10))
        publishTaxRun(next, beginTaxRun(next))
        fun member(value: PayrollMember) =
            value.copy(csrf = login(value.client, "${value.account}@example.test"))
        val maker = member(submitted.draft.source.maker)
        fun renew(scene: AmendmentReviewScene): AmendmentReviewScene {
            return scene.copy(
                draft = scene.draft.copy(source = scene.draft.source.copy(maker = maker)),
                reviewer = member(scene.reviewer),
                secondReviewer = member(scene.secondReviewer),
            )
        }
        val live = renew(submitted)
        val freshWaiting = renew(waiting)
        val freshRejecting = renew(rejecting)
        payrollError(decideAmendmentReview(live), 409, "payroll_amendment_sources_changed")
        payrollError(submitAmendmentReview(freshWaiting), 409, "payroll_amendment_sources_changed")
        assertEquals(2, payrollBody(submitAmendmentReview(live))["version"].asLong())
        payrollBody(withdrawAmendmentReview(live))
        payrollBody(
            decideAmendmentReview(freshRejecting, member = live.reviewer, decision = "REJECT")
        )
        assertEquals(
            "CALCULATED",
            draftView(live.draft)["amendment"]["summary"]["status"].asString(),
        )
    }

    @Test
    fun eightRetainedReviewsBoundResubmissionWithoutPreventingCancellation() {
        var scene = amendmentReviewScene(submitted = false)
        var version = 1L
        repeat(8) {
            payrollBody(submitAmendmentReview(scene, version))
            payrollBody(withdrawAmendmentReview(scene, version + 1))
            version += 2
            scene = scene.copy(id = UUID.randomUUID(), operation = UUID.randomUUID())
        }
        val counts = amendmentReviewCounts(scene)
        payrollError(submitAmendmentReview(scene, version), 409, "payroll_amendment_review_limit")
        assertEquals(counts, amendmentReviewCounts(scene))
        assertFalse(
            draftView(scene.draft)["availableActions"].toList().any { it.asString() == "SUBMIT" }
        )
        payrollBody(draftCommand(scene.draft, "cancel", version))
        assertEquals(
            8,
            payrollBody(get(scene.draft.source.maker.client, amendmentReviewsPath(scene))).size(),
        )
    }
}
