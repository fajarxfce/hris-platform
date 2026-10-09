package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollReviewTransactionHttpTest : PayrollReviewApiFixture() {
    @Test
    fun submissionDecisionAndWithdrawalRollbackTheirEvidenceReceiptsAndAuditTogether() {
        val f = calculationFixture()
        val run = calculated(f)
        reviewTemplate(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val tables =
            listOf(
                "payroll_reviews",
                "payroll_review_changes",
                "approval_requests",
                "approval_decisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        val before = tables.associateWith { count(f.people.payroll.company, it) }
        reviewProbe.omitChange = true
        assertEquals(409, submitReview(f, run, id, key = key).statusCode())
        reviewProbe.clear()
        assertEquals(before, tables.associateWith { count(f.people.payroll.company, it) })
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.review_submitted")
                throw DataIntegrityViolationException("Owned submission failure")
        }
        assertEquals(409, submitReview(f, run, id, key = key).statusCode())
        payrollProbe.clear()
        assertEquals(before, tables.associateWith { count(f.people.payroll.company, it) })
        payrollBody(submitReview(f, run, id, key = key))
        for (withdraw in listOf(false, true)) {
            val prior = reviewView(f, id)
            val counts = tables.associateWith { count(f.people.payroll.company, it) }
            val operation = UUID.randomUUID()
            val version = prior["review"]["version"].asLong()
            val approvalVersion = prior["approval"]["version"].asLong()
            payrollProbe.beforeJournal = {
                if (
                    it.action ==
                        if (withdraw) "payroll.review_withdrawn" else "payroll.review_decided"
                )
                    throw DataIntegrityViolationException("Owned action failure")
            }
            val failed =
                if (withdraw) withdrawReview(f, id, version, approvalVersion, operation)
                else
                    decideReview(
                        f,
                        id,
                        version = version,
                        approvalVersion = approvalVersion,
                        key = operation,
                    )
            assertEquals(409, failed.statusCode(), failed.body())
            payrollProbe.clear()
            assertEquals(prior, reviewView(f, id))
            assertEquals(counts, tables.associateWith { count(f.people.payroll.company, it) })
            payrollBody(
                if (withdraw) withdrawReview(f, id, version, approvalVersion, operation)
                else
                    decideReview(
                        f,
                        id,
                        version = version,
                        approvalVersion = approvalVersion,
                        key = operation,
                    )
            )
        }
    }

    @Test
    fun immutableEvidenceAndRlsRejectForgedApprovalAndRunTransitions() {
        val f = calculationFixture()
        val run = calculated(f)
        val id = pendingReview(f, run)
        val view = reviewView(f, id)
        val approval = UUID.fromString(view["approval"]["id"].asString())
        for (statement in
            listOf(
                "update payroll_reviews set take_home=take_home+1 where company_id=? and id=?",
                "update payroll_reviews set status='APPROVED',version=1 where company_id=? and id=?",
                "delete from payroll_reviews where company_id=? and id=?",
            )) {
            assertThrows(DataAccessException::class.java) {
                database().update(statement, f.people.payroll.company, id)
            }
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update approval_requests set status='APPROVED',version=version+1 where company_id=? and id=?",
                    f.people.payroll.company,
                    approval,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update payroll_runs set status='ABANDONED',version=version+1 where company_id=? and id=?",
                    f.people.payroll.company,
                    run,
                )
        }
        val other = calculationFixture()
        val hidden =
            transactions.run(payrollActor(other.people.payroll, other.people.preparer)) {
                Result.Success(
                    runtimeJdbc.queryForObject(
                        "select count(*) from payroll_reviews where id=?",
                        Int::class.java,
                        id,
                    )
                )
            }
        assertEquals(Result.Success(0), hidden)
        payrollError(
            get(other.people.reviewer.client, reviewsPath(other) + "/$id"),
            404,
            "payroll_review_not_found",
        )
        payrollBody(decideReview(f, id))
        assertEquals("APPROVED", reviewView(f, id)["review"]["status"].asString())
    }

    @Test
    fun competingSubmissionsAndDecisionsCommitOnceAndReplayTheSameReceipt() {
        val f = calculationFixture()
        val run = calculated(f)
        reviewTemplate(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2)
                    .map {
                        pool.submit<java.net.http.HttpResponse<String>> {
                            submitReview(f, run, id, key = key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(results[0], results[1])
        }
        payrollError(submitReview(f, run), 409, "payroll_review_active")
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2)
                    .map { pool.submit<java.net.http.HttpResponse<String>> { decideReview(f, id) } }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), results.map { it.statusCode() }.sorted())
        }
        assertEquals(1, count(f.people.payroll.company, "approval_decisions"))
        assertEquals(2, count(f.people.payroll.company, "payroll_review_changes"))
    }

    @Test
    fun lateInterruptionRollsBackSubmissionAndTheWorkerThreadCanBeReused() {
        val f = calculationFixture()
        val run = calculated(f)
        reviewTemplate(f)
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        reviewProbe.afterChange = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Owned interruption")
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    submitReviewUseCase.execute(
                        payrollActor(f.people.payroll, f.people.preparer),
                        operation,
                        id,
                        run,
                        1,
                        "Reviewed results",
                    )
                }
            val failure =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(InterruptedException::class.java, failure.cause)
            assertFalse(
                pool
                    .submit<Boolean> { Thread.currentThread().isInterrupted }
                    .get(5, TimeUnit.SECONDS)
            )
        }
        reviewProbe.clear()
        assertEquals(0, count(f.people.payroll.company, "payroll_reviews"))
        assertEquals(0, count(f.people.payroll.company, "approval_requests"))
        val retried =
            submitReviewUseCase.execute(
                payrollActor(f.people.payroll, f.people.preparer),
                operation,
                id,
                run,
                1,
                "Reviewed results",
            )
        assertTrue(retried is Result.Success, retried.toString())
    }

    @Test
    fun repeatedWithdrawalsAreBoundedAndHistoryUsesFinitePages() {
        val f = calculationFixture()
        val run = calculated(f)
        reviewTemplate(f)
        repeat(8) { number ->
            val id = UUID.fromString(payrollBody(submitReview(f, run))["id"].asString())
            assertEquals(number + 1, reviewView(f, id)["review"]["number"].asInt())
            payrollBody(withdrawReview(f, id))
        }
        payrollError(submitReview(f, run), 409, "payroll_review_capacity")
        val first = payrollBody(get(f.people.reviewer.client, runPath(f, run) + "/reviews?limit=3"))
        assertEquals(3, first["items"].size())
        assertEquals("3", first["nextCursor"].asString())
        val last =
            payrollBody(get(f.people.reviewer.client, runPath(f, run) + "/reviews?after=6&limit=3"))
        assertEquals(2, last["items"].size())
        assertTrue(last["nextCursor"].isNull)
        payrollError(
            get(f.people.reviewer.client, runPath(f, run) + "/reviews?limit=201"),
            422,
            "invalid_page",
        )
        assertEquals(8, count(f.people.payroll.company, "payroll_reviews"))
        assertEquals(16, count(f.people.payroll.company, "payroll_review_changes"))
    }
}
