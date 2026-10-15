package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollAmendmentReviewTransactionHttpTest : PayrollAmendmentReviewApiFixture() {
    @Test
    fun submissionRequiresApprovalReviewHistoryDraftRevisionAndJournalInOneCommit() {
        val scene = amendmentReviewScene(submitted = false)
        val before = amendmentReviewCounts(scene)
        for (omission in listOf("review", "change", "revision", "journal")) {
            when (omission) {
                "review" -> amendmentReviewProbe.omitReview = true
                "change" -> amendmentReviewProbe.omitChange = true
                "revision" -> draftProbe.omitRevision = true
                else -> payrollProbe.beforeJournal = {
                        if (it.action == "payroll.amendment_review_submitted")
                            throw DataIntegrityViolationException("Owned review fault")
                    }
            }
            val failed = submitAmendmentReview(scene)
            assertEquals(409, failed.statusCode(), "$omission: ${failed.body()}")
            amendmentReviewProbe.clear()
            draftProbe.clear()
            payrollProbe.clear()
            assertEquals(before, amendmentReviewCounts(scene))
        }
        val receipt = payrollBody(submitAmendmentReview(scene))
        assertEquals(receipt, payrollBody(submitAmendmentReview(scene)))
        assertEquals(
            "IN_REVIEW",
            draftView(scene.draft)["amendment"]["summary"]["status"].asString(),
        )
    }

    @Test
    fun decisionsAndWithdrawalCannotCommitPartialApprovalOrAmendmentState() {
        val scene = amendmentReviewScene()
        val before = amendmentReviewCounts(scene)
        for (action in listOf("approve", "reject", "withdraw")) {
            for (omission in listOf("change", "revision", "journal")) {
                when (omission) {
                    "change" -> amendmentReviewProbe.omitChange = true
                    "revision" -> draftProbe.omitRevision = true
                    else -> payrollProbe.beforeJournal = {
                            if (it.action.startsWith("payroll.amendment_review_"))
                                throw DataIntegrityViolationException("Owned review fault")
                        }
                }
                val response =
                    if (action == "withdraw") withdrawAmendmentReview(scene)
                    else
                        decideAmendmentReview(
                            scene,
                            decision = if (action == "approve") "APPROVE" else "REJECT",
                        )
                assertEquals(409, response.statusCode(), "$action/$omission: ${response.body()}")
                amendmentReviewProbe.clear()
                draftProbe.clear()
                payrollProbe.clear()
                assertEquals(before, amendmentReviewCounts(scene))
                assertEquals("PENDING", amendmentReviewView(scene)["review"]["status"].asString())
            }
        }
        payrollBody(decideAmendmentReview(scene))
        payrollBody(withdrawAmendmentReview(scene, 3, 1, 1))
    }

    @Test
    fun storageRejectsForgedReviewAmountsDatesActorsVersionsAndDecisionReferences() {
        val scene = amendmentReviewScene(submitted = false)
        val before = amendmentReviewCounts(scene)
        for (corruption in listOf("amount", "date", "actor", "version", "calculation")) {
            amendmentReviewProbe.transformReview = { row ->
                row.apply {
                    when (corruption) {
                        "amount" -> exposure += BigDecimal.ONE
                        "date" -> referenceDate = referenceDate.minusDays(1)
                        "actor" -> submittedBy = scene.reviewer.account
                        "version" -> amendmentVersion += 1
                        else -> calculationId = UUID.randomUUID()
                    }
                }
            }
            val failed = submitAmendmentReview(scene)
            assertEquals(409, failed.statusCode(), "$corruption: ${failed.body()}")
            amendmentReviewProbe.clear()
            assertEquals(before, amendmentReviewCounts(scene))
        }
        payrollBody(submitAmendmentReview(scene))
        val submitted = amendmentReviewCounts(scene)
        for (corruption in listOf("actor", "amendment", "approval", "decision", "time")) {
            amendmentReviewProbe.transformChange = { row ->
                row.apply {
                    when (corruption) {
                        "actor" -> actorId = scene.secondReviewer.account
                        "amendment" -> amendmentRevision -= 1
                        "approval" -> approvalVersion += 1
                        "decision" -> decidingFor = scene.secondReviewer.account
                        else -> recordedAt = recordedAt.plusSeconds(1)
                    }
                }
            }
            val failed = decideAmendmentReview(scene)
            assertEquals(409, failed.statusCode(), "$corruption: ${failed.body()}")
            amendmentReviewProbe.clear()
            assertEquals(submitted, amendmentReviewCounts(scene))
        }
        payrollBody(decideAmendmentReview(scene))
        for (statement in
            listOf(
                "delete from payroll_amendment_reviews where company_id=?",
                "update payroll_amendment_review_changes set reason='Replacement' where company_id=?",
                "delete from payroll_amendment_review_changes where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, scene.draft.source.payroll.company)
        }
    }

    @Test
    fun interruptedSubmissionRollsBackAndTheExecutorCanRetryTheSameOperation() {
        val scene = amendmentReviewScene(submitted = false)
        val actor = payrollActor(scene.draft.source.payroll, scene.draft.source.maker)
        val before = amendmentReviewCounts(scene)
        amendmentReviewProbe.afterChange = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Owned amendment review interruption")
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    submitAmendmentReviewUseCase.execute(
                        actor,
                        scene.operation,
                        scene.draft.id,
                        scene.id,
                        1,
                        scene.calculationId,
                        "Submit retained correction",
                    )
                }
            val failed =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(InterruptedException::class.java, failed.cause)
            amendmentReviewProbe.clear()
            assertEquals(before, amendmentReviewCounts(scene))
            val retried =
                pool
                    .submit<Result<MutationReceipt>> {
                        assertFalse(Thread.currentThread().isInterrupted)
                        submitAmendmentReviewUseCase.execute(
                            actor,
                            scene.operation,
                            scene.draft.id,
                            scene.id,
                            1,
                            scene.calculationId,
                            "Submit retained correction",
                        )
                    }
                    .get(10, TimeUnit.SECONDS)
            assertTrue(retried is Result.Success, retried.toString())
        }
        assertEquals("PENDING", amendmentReviewView(scene)["review"]["status"].asString())
    }
}
