package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class ExpenseReviewHttpTest : ExpenseReviewApiFixture() {
    @Test
    fun stageDecisionsAndLostResponsesRetainIndependentImmutableReviewEvidence() {
        val f = expenseFixture()
        val manager = reviewer(f)
        val finance = reviewer(f, adminAccount())
        expenseTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf("assignment" to "MANAGER"),
                            mapOf("assignment" to "NAMED", "accountIds" to listOf(finance.account)),
                        )
                ),
        )
        val id = pendingExpense(f)
        val key = UUID.randomUUID()
        val first = reviewExpense(f, id, manager, key = key)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(first.body(), reviewExpense(f, id, manager, key = key).body())
        val partial = submissionDetails(f, id)
        assertEquals("PENDING", partial.get("claimStatus").asString())
        assertEquals(1, partial.get("approval").get("currentStep").asInt())
        assertEquals(2, partial.get("claimVersion").asInt())
        assertEquals(1, partial.get("reviews").size())
        assertEquals(
            manager.account.toString(),
            partial.get("reviews")[0].get("decidingFor").asString(),
        )
        assertEquals(403, reviewExpense(f, id, manager, 2, 1).statusCode())
        val finished = reviewExpense(f, id, finance, 2, 1)
        assertEquals(200, finished.statusCode(), finished.body())
        val approved = submissionDetails(f, id)
        assertEquals("APPROVED", approved.get("claimStatus").asString())
        assertEquals(2, approved.get("reviews").size())
        assertEquals(3, approved.get("claimVersion").asInt())
        assertEquals(first.body(), reviewExpense(f, id, manager, key = key).body())
        assertEquals(409, withdrawExpense(f, id, 3).statusCode())
        assertEquals(409, cancelExpense(f, 3).statusCode())
        assertThrows(DataAccessException::class.java) {
            database().update("delete from expense_reviews where company_id=?", f.company)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_reviews set reason='Replacement' where company_id=?",
                    f.company,
                )
        }
    }

    @Test
    fun returnRequiresAReasonAndNewDraftBeforeAnIndependentResubmission() {
        val f = expenseFixture()
        val reviewer = reviewer(f)
        expenseTemplate(f)
        val lines = readyLines(f)
        val id = pendingExpense(f, lines)
        assertEquals(422, reviewExpense(f, id, reviewer, decision = "RETURN").statusCode())
        val returned =
            reviewExpense(
                f,
                id,
                reviewer,
                decision = "RETURN",
                reason = "Clarify the transaction date",
            )
        assertEquals(200, returned.statusCode(), returned.body())
        val old = submissionDetails(f, id)
        assertEquals("RETURNED", old.get("claimStatus").asString())
        assertEquals("REJECTED", old.get("approval").get("status").asString())
        assertEquals("RETURN", old.get("reviews")[0].get("decision").asString())
        assertEquals(409, submitExpense(f, UUID.randomUUID(), 2).statusCode())
        val changed =
            saveExpense(f, 2, lines = lines, changes = mapOf("title" to "Corrected travel details"))
        assertEquals(200, changed.statusCode(), changed.body())
        assertEquals("DRAFT", expenseDetails(f).get("status").asString())
        val next = UUID.randomUUID()
        assertEquals(200, submitExpense(f, next, 3).statusCode())
        assertEquals(409, reviewExpense(f, id, reviewer, 4, 1).statusCode())
        assertEquals(200, reviewExpense(f, next, reviewer, 4, 0).statusCode())
        val previous = submissionDetails(f, id)
        assertEquals("Travel reimbursement", previous.get("title").asString())
        assertEquals(old.get("lines"), previous.get("lines"))
        assertEquals(old.get("reviews"), previous.get("reviews"))
        assertEquals("Corrected travel details", submissionDetails(f, next).get("title").asString())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from expense_drafts where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun rejectedClaimsAreTerminalAndReturnedClaimsCanStillBeCancelled() {
        val f = expenseFixture()
        val reviewer = reviewer(f)
        expenseTemplate(f)
        val id = pendingExpense(f)
        assertEquals(422, reviewExpense(f, id, reviewer, decision = "REJECT").statusCode())
        val result =
            reviewExpense(
                f,
                id,
                reviewer,
                decision = "REJECT",
                reason = "Outside reimbursement policy",
            )
        assertEquals(200, result.statusCode(), result.body())
        assertEquals("REJECTED", expenseDetails(f).get("status").asString())
        assertEquals(409, saveExpense(f, 2).statusCode())
        assertEquals(409, cancelExpense(f, 2).statusCode())
        assertEquals(409, submitExpense(f, UUID.randomUUID(), 2).statusCode())
        val other = f.copy(claim = UUID.randomUUID())
        val returnId = pendingExpense(other)
        assertEquals(
            200,
            reviewExpense(
                    other,
                    returnId,
                    reviewer,
                    decision = "RETURN",
                    reason = "Receipt clarification",
                )
                .statusCode(),
        )
        assertEquals(200, cancelExpense(other, 2).statusCode())
        assertEquals("CANCELLED", expenseDetails(other).get("status").asString())
    }

    @Test
    fun duplicateSignalsAreRecomputedAndAcknowledgementsAreImmutable() {
        val f = expenseFixture()
        val reviewer = reviewer(f)
        expenseTemplate(f)
        val receipt = readyReceipt(f, pdf)
        val first = pendingExpense(f, readyLines(f, receipt))
        val other = f.copy(claim = UUID.randomUUID())
        val second = pendingExpense(other, readyLines(other, receipt))
        val detail = submissionDetails(f, first)
        assertFalse(detail.get("lines")[0].get("receipts")[0].get("possibleDuplicate").asBoolean())
        assertEquals(1, detail.get("duplicateReceiptDigests").size())
        val denied = reviewExpense(f, first, reviewer)
        assertEquals(422, denied.statusCode(), denied.body())
        assertEquals(
            "expense_duplicate_acknowledgement_required",
            json.readTree(denied.body()).get("code").asString(),
        )
        assertEquals(422, reviewExpense(f, first, reviewer, acknowledge = true).statusCode())
        val reason = "Reviewed the shared receipt and confirmed the claimed portion"
        val approved = reviewExpense(f, first, reviewer, reason = reason, acknowledge = true)
        assertEquals(200, approved.statusCode(), approved.body())
        val evidence = submissionDetails(f, first).get("reviews")[0]
        assertTrue(evidence.get("duplicatesAcknowledged").asBoolean())
        assertEquals(reason, evidence.get("reason").asString())
        assertEquals(1, evidence.get("duplicateDigests").size())
        assertEquals(200, withdrawExpense(other, second, 1).statusCode())
        val later = submissionDetails(f, first)
        assertEquals(0, later.get("duplicateReceiptDigests").size())
        assertEquals(evidence, later.get("reviews")[0])
        val resubmitted = UUID.randomUUID()
        assertEquals(200, submitExpense(other, resubmitted, 2).statusCode())
        assertEquals(422, reviewExpense(other, resubmitted, reviewer, 3).statusCode())
    }

    @Test
    fun withdrawnOtherClaimsDoNotForceAcknowledgementOfAStaleSignal() {
        val f = expenseFixture()
        val reviewer = reviewer(f)
        expenseTemplate(f)
        val receipt = readyReceipt(f, pdf)
        val first = pendingExpense(f, readyLines(f, receipt))
        val other = f.copy(claim = UUID.randomUUID())
        val second = pendingExpense(other, readyLines(other, receipt))
        assertTrue(
            submissionDetails(other, second)
                .get("lines")[0]
                .get("receipts")[0]
                .get("possibleDuplicate")
                .asBoolean()
        )
        assertEquals(200, withdrawExpense(f, first, 1).statusCode())
        val result = reviewExpense(other, second, reviewer)
        assertEquals(200, result.statusCode(), result.body())
        assertFalse(
            submissionDetails(other, second)
                .get("reviews")[0]
                .get("duplicatesAcknowledged")
                .asBoolean()
        )
    }

    @Test
    fun reassignmentInvalidatesTheApprovalVersionEvenWhenTheReviewerRemainsAssigned() {
        val f = expenseFixture()
        val reviewer = reviewer(f)
        expenseTemplate(f)
        val id = pendingExpense(f)
        val approval =
            UUID.fromString(submissionDetails(f, id).get("approval").get("id").asString())
        val reassigned = reassignExpense(f, approval, setOf(reviewer.account))
        assertEquals(200, reassigned.statusCode(), reassigned.body())
        val stale = reviewExpense(f, id, reviewer)
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("approval_changed", json.readTree(stale.body()).get("code").asString())
        assertEquals(200, reviewExpense(f, id, reviewer, approvalVersion = 1).statusCode())
    }

    @Test
    fun concurrentReviewAndWithdrawalCommitOnlyOneBusinessOutcome() {
        val f = expenseFixture()
        val reviewer = reviewer(f)
        expenseTemplate(f)
        val id = pendingExpense(f)
        val go = CountDownLatch(1)
        val outcomes =
            Executors.newFixedThreadPool(2).use { pool ->
                val review =
                    pool.submit<Int> {
                        check(go.await(5, TimeUnit.SECONDS))
                        reviewExpense(f, id, reviewer).statusCode()
                    }
                val withdraw =
                    pool.submit<Int> {
                        check(go.await(5, TimeUnit.SECONDS))
                        withdrawExpense(f, id, 1).statusCode()
                    }
                go.countDown()
                listOf(review.get(15, TimeUnit.SECONDS), withdraw.get(15, TimeUnit.SECONDS))
            }
        assertEquals(listOf(200, 409), outcomes.sorted())
        val detail = submissionDetails(f, id)
        assertEquals(
            if (outcomes[0] == 200) "APPROVED" else "DRAFT",
            detail.get("claimStatus").asString(),
        )
        assertEquals(if (outcomes[0] == 200) 1 else 0, detail.get("reviews").size())
        assertEquals(2, detail.get("claimVersion").asInt())
    }

    @Test
    fun competingReviewersCannotDuplicateAnApprovalStep() {
        val f = expenseFixture()
        val one = reviewer(f)
        val two = reviewer(f, adminAccount())
        expenseTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to listOf(one.account, two.account),
                            )
                        )
                ),
        )
        val id = pendingExpense(f)
        val go = CountDownLatch(1)
        val outcomes =
            Executors.newFixedThreadPool(2).use { pool ->
                val tasks =
                    listOf(one, two).map { r ->
                        pool.submit<Int> {
                            check(go.await(5, TimeUnit.SECONDS))
                            reviewExpense(f, id, r).statusCode()
                        }
                    }
                go.countDown()
                tasks.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), outcomes.sorted())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_reviews where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from approval_decisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun failedReviewAuditRollsBackApprovalClaimEvidenceAndOperationReceipt() {
        val f = expenseFixture()
        val reviewer = reviewer(f)
        expenseTemplate(f)
        val id = pendingExpense(f)
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_expense_review_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='${f.claim}'::uuid and new.action='expenses.submission_reviewed' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger expense_review_audit_probe before insert on audit_entries for each row execute function fail_expense_review_audit()"
            )
        try {
            val failed = reviewExpense(f, id, reviewer, key = key)
            assertEquals(409, failed.statusCode(), failed.body())
            val current = submissionDetails(f, id)
            assertEquals("PENDING", current.get("claimStatus").asString())
            assertEquals("PENDING", current.get("approval").get("status").asString())
            assertEquals(0, current.get("reviews").size())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from approval_decisions where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
        } finally {
            database().execute("drop trigger expense_review_audit_probe on audit_entries")
            database().execute("drop function fail_expense_review_audit()")
        }
        val retried = reviewExpense(f, id, reviewer, key = key)
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals(retried.body(), reviewExpense(f, id, reviewer, key = key).body())
    }

    @Test
    fun aHeaderTransitionCannotCommitWithoutMatchingReviewEvidence() {
        val f = expenseFixture()
        expenseTemplate(f)
        pendingExpense(f)
        requireNotNull(database().dataSource).connection.use { connection ->
            connection.autoCommit = false
            try {
                connection
                    .prepareStatement(
                        "update expense_claims set status='APPROVED',version=version+1 where company_id=? and id=?"
                    )
                    .use { statement ->
                        statement.setObject(1, f.company)
                        statement.setObject(2, f.claim)
                        assertEquals(1, statement.executeUpdate())
                    }
                connection
                    .prepareStatement(
                        "insert into expense_claim_changes(company_id,claim_id,version,draft_revision,status,kind,actor_id,reason,recorded_at,submission_id) select company_id,id,version,draft_revision,status,'REVIEWED',?,'',now(),latest_submission_id from expense_claims where company_id=? and id=?"
                    )
                    .use { statement ->
                        statement.setObject(1, f.managerAccount)
                        statement.setObject(2, f.company)
                        statement.setObject(3, f.claim)
                        assertEquals(1, statement.executeUpdate())
                    }
                val failure =
                    assertThrows(java.sql.SQLException::class.java) { connection.commit() }
                assertEquals("23514", failure.sqlState)
            } finally {
                connection.rollback()
            }
        }
        assertEquals("PENDING", expenseDetails(f).get("status").asString())
        assertEquals(1, expenseDetails(f).get("version").asInt())
    }
}
