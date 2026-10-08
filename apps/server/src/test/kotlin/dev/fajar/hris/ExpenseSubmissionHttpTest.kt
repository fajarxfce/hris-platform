package dev.fajar.hris

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(AccountLockProbeConfiguration::class)
class ExpenseSubmissionHttpTest : ExpenseSubmissionApiFixture() {
    @Autowired private lateinit var probe: AccountLockProbe

    @Test
    fun submissionFreezesReadyEvidencePoliciesAndCostCenterLabels() {
        val f = expenseFixture()
        val receipt = readyReceipt(f)
        val template = expenseTemplate(f)
        assertEquals(200, saveExpense(f, lines = readyLines(f, receipt)).statusCode())
        val reads = storageProbe.reads.get()
        val id = UUID.randomUUID()
        val result = submitExpense(f, id)
        assertEquals(200, result.statusCode(), result.body())
        assertEquals(reads, storageProbe.reads.get())
        val detail = submissionDetails(f, id)
        assertEquals("PENDING", detail.get("claimStatus").asString())
        assertEquals("PENDING", detail.get("approval").get("status").asString())
        assertEquals(template.toString(), detail.get("approval").get("templateId").asString())
        assertEquals(
            f.managerAccount.toString(),
            detail.get("approval").get("stages")[0][0].asString(),
        )
        assertEquals("150000.00", detail.get("totalAmount").asString())
        val frozen = detail.get("lines")[0]
        assertEquals(receipt.toString(), frozen.get("receipts")[0].get("revisionId").asString())
        assertEquals("evidence.pdf", frozen.get("receipts")[0].get("fileName").asString())
        assertEquals(64, frozen.get("receipts")[0].get("sha256").asString().length)
        assertFalse(frozen.get("receipts")[0].get("possibleDuplicate").asBoolean())
        assertFalse(detail.toString().contains("writeXid"))
        assertFalse(detail.toString().contains("objectKey"))
        val header = expenseDetails(f)
        assertEquals(1, header.get("submissionCount").asInt())
        assertEquals(id.toString(), header.get("latestSubmissionId").asString())
        assertEquals(409, saveExpense(f, 1).statusCode())
        assertEquals(409, cancelExpense(f, 1).statusCode())
        expensePolicy(
            f,
            changes =
                mapOf(
                    "name" to "Revised travel",
                    "maximumLineAmount" to "100.00",
                    "maximumClaimAmount" to "100.00",
                    "active" to false,
                ),
        )
        val updated =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/organization-units/${f.costCenter}",
                json.writeValueAsString(
                    mapOf(
                        "code" to frozen.get("costCenter").get("code").asString(),
                        "name" to "Renamed operations",
                        "kind" to "COST_CENTER",
                        "expectedVersion" to 0,
                        "active" to false,
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, updated.statusCode(), updated.body())
        expenseTemplate(f, template, mapOf("expectedVersion" to 0, "active" to false))
        val after = submissionDetails(f, id)
        assertEquals(frozen, after.get("lines")[0])
        assertEquals(detail.get("approval"), after.get("approval"))
        val inbox =
            json
                .readTree(get(f.supervisor, "/api/v1/companies/${f.company}/approvals").body())
                .get("items")
        assertTrue(inbox.any { it.get("resourceId").asString() == id.toString() })
        for (table in
            listOf(
                "expense_submissions",
                "expense_submitted_lines",
                "expense_submitted_receipts",
            )) {
            assertThrows(DataAccessException::class.java) {
                database().update("delete from $table where company_id=?", f.company)
            }
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_submissions set reason='Changed' where company_id=? and id=?",
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into expense_submitted_lines select * from expense_submitted_lines where company_id=? and submission_id=?",
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into expense_submitted_receipts select * from expense_submitted_receipts where company_id=? and submission_id=?",
                    f.company,
                    id,
                )
        }
    }

    @Test
    fun incompleteInvalidAndUnreadyDraftsNeverCreateApprovalRequests() {
        val f = expenseFixture()
        expenseTemplate(f)
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        val receipt = readyReceipt(f)
        val pending = expenseReceipt(f)
        val cases =
            listOf(
                emptyList<Map<String, Any?>>() to "expense_lines_required",
                listOf(expenseLine(f)) to "expense_receipt_required",
                listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(pending)))) to
                    "expense_receipt_not_ready",
                listOf(expenseLine(f, mapOf("occurredOn" to today.plusDays(1)))) to
                    "expense_transaction_date_invalid",
                listOf(expenseLine(f, mapOf("occurredOn" to today.minusDays(367)))) to
                    "expense_transaction_date_invalid",
                listOf(
                    expenseLine(
                        f,
                        mapOf(
                            "occurredOn" to today.minusDays(31),
                            "receiptRevisionIds" to listOf(receipt),
                        ),
                    )
                ) to "expense_transaction_expired",
                listOf(
                    expenseLine(
                        f,
                        mapOf("amount" to "500000.01", "receiptRevisionIds" to listOf(receipt)),
                    )
                ) to "expense_line_limit_exceeded",
                listOf(
                    expenseLine(
                        f,
                        mapOf("costCenterId" to null, "receiptRevisionIds" to listOf(receipt)),
                    )
                ) to "expense_cost_center_required",
            )
        for ((lines, code) in cases) {
            val claim = f.copy(claim = UUID.randomUUID())
            val saved = saveExpense(claim, lines = lines)
            assertEquals(200, saved.statusCode(), saved.body())
            val result = submitExpense(claim, UUID.randomUUID())
            assertEquals(422, result.statusCode(), result.body())
            assertEquals(code, json.readTree(result.body()).get("code").asString())
            assertEquals("DRAFT", expenseDetails(claim).get("status").asString())
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from approval_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_submissions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun limitsUseEveryEffectiveRevisionAndMixedRoutingCannotSkipACategory() {
        val f = expenseFixture()
        val receipt = readyReceipt(f)
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        expensePolicy(f, changes = mapOf("maximumClaimAmount" to "500000.00"))
        expensePolicy(
            f,
            version = 1,
            changes = mapOf("effectiveFrom" to today, "maximumClaimAmount" to "800000.00"),
        )
        val lines =
            listOf(
                expenseLine(
                    f,
                    mapOf(
                        "amount" to "300000.00",
                        "occurredOn" to today.minusDays(1),
                        "receiptRevisionIds" to listOf(receipt),
                    ),
                ),
                expenseLine(
                    f,
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "amount" to "300000.00",
                        "receiptRevisionIds" to listOf(receipt),
                    ),
                ),
            )
        assertEquals(200, saveExpense(f, lines = lines).statusCode())
        val failed = submitExpense(f, UUID.randomUUID())
        assertEquals(422, failed.statusCode(), failed.body())
        assertEquals(
            "expense_category_limit_exceeded",
            json.readTree(failed.body()).get("code").asString(),
        )
        val second = UUID.randomUUID()
        expensePolicy(f, second, null, mapOf("code" to "MEALS", "name" to "Business meals"))
        val template = expenseTemplate(f)
        val specific =
            expenseTemplate(
                f,
                changes = mapOf("category" to "MEALS", "minimumAmount" to "250000.00"),
            )
        val mixed =
            listOf(
                expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))),
                expenseLine(
                    f,
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "categoryId" to second,
                        "receiptRevisionIds" to listOf(receipt),
                    ),
                ),
            )
        assertEquals(200, saveExpense(f, 0, lines = mixed).statusCode())
        val split = submitExpense(f, UUID.randomUUID(), 1)
        assertEquals(422, split.statusCode(), split.body())
        assertEquals(
            "expense_approval_requires_split",
            json.readTree(split.body()).get("code").asString(),
        )
        expenseTemplate(
            f,
            specific,
            mapOf(
                "category" to "MEALS",
                "minimumAmount" to "250000.00",
                "expectedVersion" to 0,
                "active" to false,
            ),
        )
        val id = UUID.randomUUID()
        val success = submitExpense(f, id, 1)
        assertEquals(200, success.statusCode(), success.body())
        assertEquals(
            template.toString(),
            submissionDetails(f, id).get("approval").get("templateId").asString(),
        )
    }

    @Test
    fun contributorHistoryExcludesEarlierMakersEvenAfterAnotherEditorSaves() {
        val f = expenseFixture()
        val receipt = readyReceipt(f)
        expenseTemplate(f)
        val lines = readyLines(f, receipt)
        assertEquals(200, saveExpense(f, lines = lines).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.manage')",
                f.company,
                f.managerAccount,
            )
        val csrf = login(f.supervisor, "${f.managerAccount}@example.test")
        val edited =
            command(
                f.supervisor,
                "${f.path}/${f.claim}/draft",
                expenseBody(f, 0, lines, mapOf("title" to "Manager correction")),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, edited.statusCode(), edited.body())
        assertEquals(200, saveExpense(f, 1, lines = lines).statusCode())
        val id = UUID.randomUUID()
        val result = submitExpense(f, id, 2)
        assertEquals(200, result.statusCode(), result.body())
        val detail = submissionDetails(f, id)
        assertEquals("BLOCKED", detail.get("approval").get("status").asString())
        assertEquals(
            setOf(f.account.toString(), f.managerAccount.toString()),
            detail.get("makerIds").iterator().asSequence().map { it.asString() }.toSet(),
        )
        assertEquals(0, detail.get("approval").get("stages")[0].size())
        assertEquals(200, withdrawExpense(f, id, 3).statusCode())
    }

    @Test
    fun duplicateDigestsSignalRelatedPendingClaimsWithoutLeakingTheirOwners() {
        val f = expenseFixture()
        expenseTemplate(f)
        val firstReceipt = readyReceipt(f, pdf)
        val secondReceipt = readyReceipt(f, pdf)
        assertEquals(200, saveExpense(f, lines = readyLines(f, firstReceipt)).statusCode())
        val first = UUID.randomUUID()
        assertEquals(200, submitExpense(f, first).statusCode())
        val other = f.copy(claim = UUID.randomUUID())
        assertEquals(200, saveExpense(other, lines = readyLines(f, secondReceipt)).statusCode())
        val second = UUID.randomUUID()
        assertEquals(200, submitExpense(other, second).statusCode())
        assertFalse(
            submissionDetails(f, first)
                .get("lines")[0]
                .get("receipts")[0]
                .get("possibleDuplicate")
                .asBoolean()
        )
        val signal = submissionDetails(other, second).get("lines")[0].get("receipts")[0]
        assertTrue(signal.get("possibleDuplicate").asBoolean())
        assertFalse(signal.toString().contains(first.toString()))
        assertEquals(200, withdrawExpense(f, first, 1).statusCode())
        assertEquals(200, withdrawExpense(other, second, 1).statusCode())
        val repeated = f.copy(claim = UUID.randomUUID())
        val lines =
            listOf(
                expenseLine(f, mapOf("receiptRevisionIds" to listOf(firstReceipt))),
                expenseLine(
                    f,
                    mapOf("id" to UUID.randomUUID(), "receiptRevisionIds" to listOf(secondReceipt)),
                ),
            )
        assertEquals(200, saveExpense(repeated, lines = lines).statusCode())
        val id = UUID.randomUUID()
        assertEquals(200, submitExpense(repeated, id).statusCode())
        assertTrue(
            submissionDetails(repeated, id).get("lines").all {
                it.get("receipts")[0].get("possibleDuplicate").asBoolean()
            }
        )
    }

    @Test
    fun competingSubmissionsAndLostResponsesKeepOneImmutableAttempt() {
        val f = expenseFixture()
        expenseTemplate(f)
        assertEquals(200, saveExpense(f, lines = readyLines(f)).statusCode())
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val go = CountDownLatch(1)
        val results =
            Executors.newFixedThreadPool(2).use { pool ->
                val pending =
                    (1..2).map {
                        pool.submit<java.net.http.HttpResponse<String>> {
                            check(go.await(5, TimeUnit.SECONDS))
                            submitExpense(f, id, key = key)
                        }
                    }
                go.countDown()
                pending.map { it.get(15, TimeUnit.SECONDS) }
            }
        results.forEach { assertEquals(200, it.statusCode(), it.body()) }
        assertEquals(results[0].body(), results[1].body())
        assertEquals(409, submitExpense(f, UUID.randomUUID()).statusCode())
        assertEquals(409, submitExpense(f, UUID.randomUUID(), key = key).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_submissions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from approval_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val withdrawKey = UUID.randomUUID()
        val withdrawn = withdrawExpense(f, id, 1, withdrawKey)
        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(withdrawn.body(), withdrawExpense(f, id, 1, withdrawKey).body())
        assertEquals(results[0].body(), submitExpense(f, id, key = key).body())
        assertEquals("CANCELLED", submissionDetails(f, id).get("approval").get("status").asString())
        assertEquals(2, expenseDetails(f).get("version").asLong())
        val secondGo = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                (1..2).map {
                    pool.submit<Int> {
                        check(secondGo.await(5, TimeUnit.SECONDS))
                        submitExpense(f, UUID.randomUUID(), 2).statusCode()
                    }
                }
            secondGo.countDown()
            assertEquals(listOf(200, 409), tasks.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from expense_submissions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(withdrawn.body(), withdrawExpense(f, id, 1, withdrawKey).body())
        assertEquals(409, withdrawExpense(f, id, 3).statusCode())
    }

    @Test
    fun oldReviewersSeeOnlyTheExactSubmissionAndLoseAccessWhenTheirGrantIsRevoked() {
        val f = expenseFixture()
        val receipt = readyReceipt(f)
        val template = expenseTemplate(f)
        assertEquals(200, saveExpense(f, lines = readyLines(f, receipt)).statusCode())
        val id = UUID.randomUUID()
        assertEquals(200, submitExpense(f, id).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.team.read'",
                f.company,
                f.managerAccount,
            )
        assertEquals(
            "Travel reimbursement",
            submissionDetails(f, id, f.supervisor).get("title").asString(),
        )
        assertEquals(404, get(f.supervisor, "${f.path}/${f.claim}").statusCode())
        assertEquals(200, withdrawExpense(f, id, 1).statusCode())
        assertEquals(
            200,
            saveExpense(
                    f,
                    2,
                    lines = readyLines(f, receipt),
                    changes = mapOf("title" to "Unsubmitted private correction"),
                )
                .statusCode(),
        )
        for (suffix in listOf("", "/drafts", "/history", "/submissions")) assertEquals(
            404,
            get(f.supervisor, "${f.path}/${f.claim}$suffix").statusCode(),
        )
        assertEquals(
            "Travel reimbursement",
            submissionDetails(f, id, f.supervisor).get("title").asString(),
        )
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        expenseTemplate(
            f,
            template,
            mapOf(
                "expectedVersion" to 0,
                "stages" to listOf(mapOf("assignment" to "NAMED", "accountIds" to listOf(admin))),
            ),
        )
        val second = UUID.randomUUID()
        assertEquals(200, submitExpense(f, second, 3).statusCode())
        assertEquals(
            404,
            get(f.supervisor, "/api/v1/companies/${f.company}/expenses/submissions/$second")
                .statusCode(),
        )
        val prior = submissionDetails(f, id, f.supervisor)
        assertFalse(prior.get("current").asBoolean())
        assertFalse(prior.toString().contains("Unsubmitted private correction"))
        val page = json.readTree(get(f.worker, "${f.path}/${f.claim}/submissions?limit=1").body())
        assertEquals("2", page.get("nextCursor").asString())
        assertEquals(second.toString(), page.get("items")[0].get("id").asString())
        val earlier =
            json.readTree(get(f.worker, "${f.path}/${f.claim}/submissions?limit=1&after=2").body())
        assertEquals(id.toString(), earlier.get("items")[0].get("id").asString())
        assertEquals(422, get(f.worker, "${f.path}/${f.claim}/submissions?limit=21").statusCode())
        val foreign = company(f.admin, f.adminCsrf)
        assertEquals(
            404,
            get(f.admin, "/api/v1/companies/$foreign/expenses/submissions/$id").statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.team.approve'",
                f.company,
                f.managerAccount,
            )
        assertEquals(
            404,
            get(f.supervisor, "/api/v1/companies/${f.company}/expenses/submissions/$id")
                .statusCode(),
        )
    }

    @Test
    fun submissionAndWithdrawalAuditFailureRollBackEveryBusinessConsequence() {
        val f = expenseFixture()
        expenseTemplate(f)
        assertEquals(200, saveExpense(f, lines = readyLines(f)).statusCode())
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_expense_submission_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='${f.claim}'::uuid and new.action in ('expenses.claim_submitted','expenses.submission_withdrawn') then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger expense_submission_audit_probe before insert on audit_entries for each row execute function fail_expense_submission_audit()"
            )
        try {
            val failed = submitExpense(f, id, key = key)
            assertEquals(409, failed.statusCode(), failed.body())
            assertEquals("DRAFT", expenseDetails(f).get("status").asString())
            for (table in
                listOf(
                    "expense_submissions",
                    "expense_submitted_lines",
                    "expense_submitted_receipts",
                    "approval_requests",
                )) assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from $table where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
                table,
            )
            database()
                .execute("alter table audit_entries disable trigger expense_submission_audit_probe")
            val submitted = submitExpense(f, id, key = key)
            assertEquals(200, submitted.statusCode(), submitted.body())
            database()
                .execute("alter table audit_entries enable trigger expense_submission_audit_probe")
            val withdrawalKey = UUID.randomUUID()
            assertEquals(409, withdrawExpense(f, id, 1, withdrawalKey).statusCode())
            val stillPending = submissionDetails(f, id)
            assertEquals("PENDING", stillPending.get("claimStatus").asString())
            assertEquals("PENDING", stillPending.get("approval").get("status").asString())
            assertEquals(1, stillPending.get("claimVersion").asInt())
            database()
                .execute("alter table audit_entries disable trigger expense_submission_audit_probe")
            assertEquals(200, withdrawExpense(f, id, 1, withdrawalKey).statusCode())
        } finally {
            database().execute("drop trigger expense_submission_audit_probe on audit_entries")
            database().execute("drop function fail_expense_submission_audit()")
        }
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select count(*) from expense_claim_changes where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun aPendingSubmissionContinuesToConsumeOpenClaimCapacity() {
        val f = expenseFixture()
        expenseTemplate(f)
        assertEquals(200, saveExpense(f, lines = readyLines(f)).statusCode())
        assertEquals(200, submitExpense(f, UUID.randomUUID()).statusCode())
        for (index in 1..49) assertEquals(
            200,
            saveExpense(f.copy(claim = UUID.randomUUID()), lines = emptyList()).statusCode(),
        )
        val excess = saveExpense(f.copy(claim = UUID.randomUUID()), lines = emptyList())
        assertEquals(429, excess.statusCode(), excess.body())
    }

    @Test
    fun boundedResubmissionsPreserveTheirHistoryAndRemainCancellable() {
        val f = expenseFixture()
        expenseTemplate(f)
        assertEquals(200, saveExpense(f, lines = readyLines(f)).statusCode())
        for (number in 1..20) {
            val id = UUID.randomUUID()
            val submitted = submitExpense(f, id, (number - 1L) * 2)
            assertEquals(200, submitted.statusCode(), submitted.body())
            val withdrawn = withdrawExpense(f, id, (number - 1L) * 2 + 1)
            assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        }
        val exhausted = submitExpense(f, UUID.randomUUID(), 40)
        assertEquals(409, exhausted.statusCode(), exhausted.body())
        assertEquals(
            "expense_submission_limit",
            json.readTree(exhausted.body()).get("code").asString(),
        )
        assertEquals(200, cancelExpense(f, 40).statusCode())
        assertEquals(20, expenseDetails(f).get("submissionCount").asInt())
        assertEquals(
            20,
            database()
                .queryForObject(
                    "select count(*) from expense_submissions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_drafts where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun pendingSubmissionRechecksRevocationBeforeChangingTheClaim() {
        val f = expenseFixture()
        expenseTemplate(f)
        assertEquals(200, saveExpense(f, lines = readyLines(f)).statusCode())
        val barrier = AccountLockProbe.Barrier(f.account)
        probe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Int> { submitExpense(f, UUID.randomUUID()).statusCode() }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                        f.company,
                        f.account,
                    )
                barrier.release.countDown()
                assertEquals(404, pending.get(15, TimeUnit.SECONDS))
            }
        } finally {
            barrier.release.countDown()
            probe.current.set(null)
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_submissions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals("DRAFT", expenseDetails(f, f.admin).get("status").asString())
    }

    @Test
    fun savingAndSubmittingTheSameVersionCannotBothCommit() {
        val f = expenseFixture()
        expenseTemplate(f)
        val lines = readyLines(f)
        assertEquals(200, saveExpense(f, lines = lines).statusCode())
        val id = UUID.randomUUID()
        val go = CountDownLatch(1)
        val result =
            Executors.newFixedThreadPool(2).use { pool ->
                val saving =
                    pool.submit<Int> {
                        check(go.await(5, TimeUnit.SECONDS))
                        saveExpense(
                                f,
                                0,
                                lines = lines,
                                changes = mapOf("title" to "Concurrent edit"),
                            )
                            .statusCode()
                    }
                val submitting =
                    pool.submit<Int> {
                        check(go.await(5, TimeUnit.SECONDS))
                        submitExpense(f, id).statusCode()
                    }
                go.countDown()
                listOf(saving.get(15, TimeUnit.SECONDS), submitting.get(15, TimeUnit.SECONDS))
            }
        assertEquals(listOf(200, 409), result.sorted())
        assertEquals(1, expenseDetails(f).get("version").asLong())
        assertEquals(
            if (result[1] == 200) 1 else 0,
            database()
                .queryForObject(
                    "select count(*) from expense_submissions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        if (result[1] == 200)
            assertEquals("Travel reimbursement", submissionDetails(f, id).get("title").asString())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from expense_claim_changes where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun candidateOverflowIsRejectedBeforeFilteringTheDraftMaker() {
        val f = expenseFixture()
        val tag = UUID.randomUUID().toString()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select gen_random_uuid(),? || n || '@example.test',?,a.password_hash from generate_series(1,200) n cross join accounts a where a.email='admin@example.test'",
                tag,
                tag,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) select ?,id from accounts where display_name=?",
                f.company,
                tag,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) select ?,id,'expenses.approve' from accounts where display_name=?",
                f.company,
                tag,
            )
        expenseTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf("assignment" to "PERMISSION", "permission" to "expenses.approve")
                        )
                ),
        )
        assertEquals(200, saveExpense(f, lines = readyLines(f), asAdmin = true).statusCode())
        val result = submitExpense(f, UUID.randomUUID(), asAdmin = true)
        assertEquals(422, result.statusCode(), result.body())
        assertEquals(
            "approval_group_too_large",
            json.readTree(result.body()).get("code").asString(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from approval_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }
}
