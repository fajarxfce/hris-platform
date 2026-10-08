package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.expenses.domain.entities.SaveExpenseDraftCommand
import dev.fajar.hris.expenses.domain.usecases.SaveExpenseDraft
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
class ExpenseDraftHttpTest : ExpenseApiFixture() {
    @Autowired private lateinit var probe: AccountLockProbe
    @Autowired private lateinit var saveDraft: SaveExpenseDraft

    @Test
    fun draftRevisionsKeepTheirLinesReceiptsAndHistoricalTotals() {
        val f = expenseFixture()
        val receipt = expenseReceipt(f)
        val lines = listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))))
        val first = saveExpense(f, lines = lines)
        assertEquals(200, first.statusCode(), first.body())
        val current = expenseDetails(f)
        assertEquals("DRAFT", current.get("status").asString())
        assertEquals("150000.00", current.get("draft").get("totalAmount").asString())
        assertEquals(
            receipt.toString(),
            current.get("draft").get("lines")[0].get("receiptRevisionIds")[0].asString(),
        )
        assertFalse(current.toString().contains("writeXid"))
        val update =
            saveExpense(
                f,
                0,
                lines =
                    listOf(
                        expenseLine(
                            f,
                            mapOf("amount" to "210000.00", "receiptRevisionIds" to listOf(receipt)),
                        )
                    ),
                changes = mapOf("title" to "Updated travel"),
            )
        assertEquals(200, update.statusCode(), update.body())
        val history = json.readTree(get(f.worker, "${f.path}/${f.claim}/drafts?limit=1").body())
        assertEquals("1", history.get("nextCursor").asString())
        assertEquals("210000.00", history.get("items")[0].get("totalAmount").asString())
        val prior =
            json
                .readTree(get(f.worker, "${f.path}/${f.claim}/drafts?limit=1&after=1").body())
                .get("items")[0]
        assertEquals("150000.00", prior.get("totalAmount").asString())
        assertEquals("Travel reimbursement", prior.get("title").asString())
        assertEquals(
            receipt.toString(),
            prior.get("lines")[0].get("receiptRevisionIds")[0].asString(),
        )
        val changes =
            json.readTree(get(f.worker, "${f.path}/${f.claim}/history").body()).get("items")
        assertEquals(2, changes.size())
        assertEquals("DRAFT_SAVED", changes[0].get("kind").asString())
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_draft_lines set amount=1 where company_id=? and claim_id=?",
                    f.company,
                    f.claim,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "delete from expense_draft_receipts where company_id=? and claim_id=?",
                    f.company,
                    f.claim,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into expense_draft_lines(company_id,claim_id,draft_revision,id,ordinal,category_id,occurred_on,amount,description) values(?,?,1,?,2,?,current_date,1,'Late append')",
                    f.company,
                    f.claim,
                    UUID.randomUUID(),
                    f.category,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_claims set employment_id=?,version=version+1,draft_revision=draft_revision+1 where company_id=? and id=?",
                    f.manager,
                    f.company,
                    f.claim,
                )
        }
    }

    @Test
    fun lostResponsesAndCompetingSavesNeverCreateDuplicateDrafts() {
        val f = expenseFixture()
        val key = UUID.randomUUID()
        val go = CountDownLatch(1)
        val first =
            Executors.newFixedThreadPool(2).use { pool ->
                val tasks =
                    (1..2).map {
                        pool.submit<java.net.http.HttpResponse<String>> {
                            check(go.await(5, TimeUnit.SECONDS))
                            saveExpense(f, key = key)
                        }
                    }
                go.countDown()
                tasks.map { it.get(15, TimeUnit.SECONDS) }
            }
        first.forEach { assertEquals(200, it.statusCode(), it.body()) }
        assertEquals(first[0].body(), first[1].body())
        val update = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                (1..2).map { index ->
                    pool.submit<Int> {
                        check(update.await(5, TimeUnit.SECONDS))
                        saveExpense(f, 0, changes = mapOf("title" to "Change $index")).statusCode()
                    }
                }
            update.countDown()
            assertEquals(listOf(200, 409), tasks.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(1, expenseDetails(f).get("version").asLong())
        assertEquals(first[0].body(), saveExpense(f, key = key).body())
        assertEquals(
            409,
            saveExpense(f, key = key, changes = mapOf("title" to "Different payload")).statusCode(),
        )
        val cancelKey = UUID.randomUUID()
        val cancelled = cancelExpense(f, 1, cancelKey)
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        assertEquals(cancelled.body(), cancelExpense(f, 1, cancelKey).body())
        assertEquals(first[0].body(), saveExpense(f, key = key).body())
        assertEquals(409, saveExpense(f, 2).statusCode())
        assertEquals("CANCELLED", expenseDetails(f).get("status").asString())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from expense_drafts where company_id=? and claim_id=?",
                    Int::class.java,
                    f.company,
                    f.claim,
                ),
        )
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select count(*) from expense_claim_changes where company_id=? and claim_id=?",
                    Int::class.java,
                    f.company,
                    f.claim,
                ),
        )
    }

    @Test
    fun claimReadsFollowCurrentTeamSelfAndCompanyScope() {
        val f = expenseFixture()
        assertEquals(200, saveExpense(f).statusCode())
        assertEquals(200, get(f.supervisor, "${f.path}/${f.claim}").statusCode())
        val otherAccount = expenseMember(f.company, listOf("expenses.self.manage"))
        val unrelated = client()
        login(unrelated, "$otherAccount@example.test")
        assertEquals(404, get(unrelated, "${f.path}/${f.claim}").statusCode())
        assertEquals(403, get(f.worker, "${f.path}?${expensePeriod()}").statusCode())
        assertEquals(
            200,
            get(f.worker, "${f.path}?${expensePeriod()}&employmentId=${f.employee}").statusCode(),
        )
        assertEquals(
            404,
            get(f.worker, "${f.path}?${expensePeriod()}&employmentId=${f.manager}").statusCode(),
        )
        assertEquals(200, get(f.admin, "${f.path}?${expensePeriod()}").statusCode())
        val foreign = company(f.admin, f.adminCsrf)
        assertEquals(
            404,
            get(f.admin, "/api/v1/companies/$foreign/expenses/claims/${f.claim}").statusCode(),
        )
        assertEquals(
            403,
            get(f.worker, "/api/v1/companies/$foreign/expenses/claims/${f.claim}").statusCode(),
        )
        assertEquals(
            409,
            saveExpense(f, 0, changes = mapOf("employmentId" to f.manager), asAdmin = true)
                .statusCode(),
        )
        val moved =
            revise(f.admin, f.adminCsrf, f.company, f.employee, 0, terms(from = "2026-01-02"))
        assertEquals(200, moved.statusCode(), moved.body())
        assertEquals(404, get(f.supervisor, "${f.path}/${f.claim}").statusCode())
        assertEquals(200, get(f.worker, "${f.path}/${f.claim}").statusCode())
        val onBehalf =
            saveExpense(f.copy(claim = UUID.randomUUID(), employee = f.manager), asAdmin = true)
        assertEquals(200, onBehalf.statusCode(), onBehalf.body())
    }

    @Test
    fun categoriesCostCentersAndReceiptsCannotReferenceAnotherCompanyOrEmployee() {
        val f = expenseFixture()
        val foreign = company(f.admin, f.adminCsrf)
        val foreignEmployee = employee(f.admin, f.adminCsrf, foreign)
        val foreignCategory = UUID.randomUUID()
        val categoryCreated =
            command(
                f.admin,
                "/api/v1/companies/$foreign/expenses/categories/$foreignCategory",
                json.writeValueAsString(
                    mapOf(
                        "code" to "TRAVEL",
                        "name" to "Foreign category",
                        "effectiveFrom" to "2026-01-01",
                        "maximumLineAmount" to "500000.00",
                        "maximumClaimAmount" to "2000000.00",
                        "reason" to "Foreign policy fixture",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, categoryCreated.statusCode(), categoryCreated.body())
        val foreignCost = expenseUnit(f.admin, f.adminCsrf, foreign)
        val department = expenseUnit(f.admin, f.adminCsrf, f.company, "DEPARTMENT")
        val foreignReceipt = expenseReceipt(f, company = foreign, employee = foreignEmployee)
        val differentOwner = expenseReceipt(f, employee = f.manager)
        val personal = expenseReceipt(f, classification = "PERSONAL")
        val cases =
            listOf(
                mapOf("categoryId" to UUID.randomUUID()),
                mapOf("categoryId" to foreignCategory),
                mapOf("costCenterId" to foreignCost),
                mapOf("costCenterId" to department),
                mapOf("receiptRevisionIds" to listOf(foreignReceipt)),
                mapOf("receiptRevisionIds" to listOf(differentOwner)),
                mapOf("receiptRevisionIds" to listOf(personal)),
            )
        for (changes in cases) {
            val result = saveExpense(f, lines = listOf(expenseLine(f, changes)))
            assertEquals(422, result.statusCode(), result.body())
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_claims where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val own = expenseReceipt(f)
        val result =
            saveExpense(
                f,
                lines = listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(own)))),
            )
        assertEquals(200, result.statusCode(), result.body())
    }

    @Test
    fun incompleteDraftsAreAllowedButPayloadsAndReadWindowsAreBounded() {
        val f = expenseFixture()
        val cases =
            listOf(
                listOf(expenseLine(f, mapOf("amount" to "-1"))),
                listOf(expenseLine(f, mapOf("amount" to "1.001"))),
                listOf(expenseLine(f, mapOf("amount" to "1e99999"))),
                listOf(expenseLine(f, mapOf("occurredOn" to "2201-01-01"))),
                listOf(expenseLine(f), expenseLine(f)),
                (1..21).map { expenseLine(f, mapOf("id" to UUID.randomUUID())) },
                listOf(
                    expenseLine(f, mapOf("receiptRevisionIds" to List(4) { UUID.randomUUID() }))
                ),
                listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(f.line, f.line)))),
                listOf(
                    expenseLine(f, mapOf("amount" to "999999999999.99")),
                    expenseLine(f, mapOf("id" to UUID.randomUUID(), "amount" to "1.00")),
                ),
            )
        for (lines in cases) {
            val result = saveExpense(f, lines = lines)
            assertEquals(422, result.statusCode(), result.body())
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_claims where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(200, saveExpense(f, lines = emptyList()).statusCode())
        assertEquals("0.00", expenseDetails(f).get("draft").get("totalAmount").asString())
        val archived =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/expenses/categories/${f.category}",
                json.writeValueAsString(
                    mapOf(
                        "code" to "TRAVEL",
                        "name" to "Business travel",
                        "effectiveFrom" to "2026-01-01",
                        "maximumLineAmount" to "500000.00",
                        "maximumClaimAmount" to "2000000.00",
                        "active" to false,
                        "expectedVersion" to 0,
                        "reason" to "Category archived",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, archived.statusCode(), archived.body())
        assertEquals(200, saveExpense(f, 0).statusCode())
        assertEquals(422, get(f.worker, "${f.path}/${f.claim}/drafts?limit=21").statusCode())
        assertEquals(422, get(f.admin, "${f.path}?from=2026-01-01&until=2027-01-02").statusCode())
        assertEquals(422, get(f.admin, "${f.path}?${expensePeriod()}&limit=201").statusCode())
    }

    @Test
    fun auditFailureRollsBackHeaderDraftLinesReceiptsAndOperationReceipt() {
        val f = expenseFixture()
        val receipt = expenseReceipt(f)
        val lines = listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))))
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_expense_draft_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='${f.claim}'::uuid and new.action='expenses.draft_saved' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger expense_draft_audit_probe before insert on audit_entries for each row execute function fail_expense_draft_audit()"
            )
        try {
            val failed = saveExpense(f, lines = lines, key = key)
            assertEquals(409, failed.statusCode(), failed.body())
            for (table in
                listOf(
                    "expense_claims",
                    "expense_drafts",
                    "expense_draft_lines",
                    "expense_draft_receipts",
                    "expense_claim_changes",
                )) {
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from $table where company_id=?",
                            Int::class.java,
                            f.company,
                        ),
                    table,
                )
            }
        } finally {
            database().execute("drop trigger expense_draft_audit_probe on audit_entries")
            database().execute("drop function fail_expense_draft_audit()")
        }
        val retried = saveExpense(f, lines = lines, key = key)
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals(retried.body(), saveExpense(f, lines = lines, key = key).body())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_draft_receipts where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun waitingCommandsRejectRevokedPermissionsAndCredentials() {
        val f = expenseFixture()
        for (credential in listOf(false, true)) {
            val barrier = AccountLockProbe.Barrier(f.account)
            probe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending = pool.submit<Int> { saveExpense(f).statusCode() }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (credential)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    else
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                                f.company,
                                f.account,
                            )
                    barrier.release.countDown()
                    assertEquals(if (credential) 401 else 403, pending.get(15, TimeUnit.SECONDS))
                }
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
            if (!credential)
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.self.manage')",
                        f.company,
                        f.account,
                    )
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_claims where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun newlyGrantedCapabilitiesRequireAFreshlyResolvedRequest() {
        val f = expenseFixture()
        val actor =
            Actor(
                f.account,
                f.company,
                setOf("company.read", "expenses.self.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.manage')",
                f.company,
                f.account,
            )
        val key = UUID.randomUUID()
        val result =
            saveDraft.execute(
                actor,
                key,
                SaveExpenseDraftCommand(
                    f.claim,
                    f.manager,
                    null,
                    "Travel reimbursement",
                    "Client visit",
                    emptyList(),
                    "Expense draft saved",
                ),
            )
        assertEquals("expense_access_denied", (result as Result.Failed).failure.code)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_claims where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val fresh =
            saveExpense(
                f,
                lines = emptyList(),
                changes = mapOf("employmentId" to f.manager),
                key = key,
            )
        assertEquals(200, fresh.statusCode(), fresh.body())
    }

    @Test
    fun cancellingAndSavingTheSameVersionCannotBothCommit() {
        val f = expenseFixture()
        assertEquals(200, saveExpense(f).statusCode())
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val saving =
                pool.submit<Int> {
                    check(go.await(5, TimeUnit.SECONDS))
                    saveExpense(f, 0).statusCode()
                }
            val cancelling =
                pool.submit<Int> {
                    check(go.await(5, TimeUnit.SECONDS))
                    cancelExpense(f, 0).statusCode()
                }
            go.countDown()
            assertEquals(
                listOf(200, 409),
                listOf(saving.get(15, TimeUnit.SECONDS), cancelling.get(15, TimeUnit.SECONDS))
                    .sorted(),
            )
        }
        assertEquals(1, expenseDetails(f).get("version").asLong())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from expense_claim_changes where company_id=? and claim_id=?",
                    Int::class.java,
                    f.company,
                    f.claim,
                ),
        )
    }

    @Test
    fun actorCapacityIsSerializedAndCancellationReleasesItsReservation() {
        val f = expenseFixture()
        val ids =
            (1..49).map {
                val id = UUID.randomUUID()
                val result = saveExpense(f.copy(claim = id), lines = emptyList())
                assertEquals(200, result.statusCode(), result.body())
                id
            }
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                (1..2).map {
                    pool.submit<Int> {
                        check(go.await(5, TimeUnit.SECONDS))
                        saveExpense(f.copy(claim = UUID.randomUUID()), lines = emptyList())
                            .statusCode()
                    }
                }
            go.countDown()
            assertEquals(listOf(200, 429), tasks.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            50,
            database()
                .queryForObject(
                    "select count(*) from expense_claims where company_id=? and status='DRAFT'",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(200, cancelExpense(f.copy(claim = ids.first()), 0).statusCode())
        assertEquals(
            200,
            saveExpense(f.copy(claim = UUID.randomUUID()), lines = emptyList()).statusCode(),
        )
        val page =
            json.readTree(
                get(f.worker, "${f.path}?${expensePeriod()}&employmentId=${f.employee}&limit=1")
                    .body()
            )
        val next = page.get("nextCursor").asString()
        val second =
            json.readTree(
                get(
                        f.worker,
                        "${f.path}?${expensePeriod()}&employmentId=${f.employee}&limit=1&after=$next",
                    )
                    .body()
            )
        assertNotEquals(
            page.get("items")[0].get("id").asString(),
            second.get("items")[0].get("id").asString(),
        )
    }

    @Test
    fun revisionLimitPreservesEvidenceAndStillAllowsCancellation() {
        val f = expenseFixture()
        assertEquals(200, saveExpense(f, lines = emptyList()).statusCode())
        for (revision in 1..99) {
            val result =
                saveExpense(
                    f,
                    (revision - 1).toLong(),
                    lines = emptyList(),
                    changes = mapOf("title" to "Draft revision $revision"),
                )
            assertEquals(200, result.statusCode(), result.body())
        }
        val exhausted = saveExpense(f, 99, lines = emptyList())
        assertEquals(409, exhausted.statusCode(), exhausted.body())
        assertEquals(
            "expense_draft_revision_limit",
            json.readTree(exhausted.body()).get("code").asString(),
        )
        assertEquals(200, cancelExpense(f, 99).statusCode())
        assertEquals(100, expenseDetails(f).get("version").asLong())
        val page =
            json.readTree(get(f.worker, "${f.path}/${f.claim}/drafts?after=80&limit=20").body())
        assertEquals(20, page.get("items").size())
        assertEquals(79, page.get("items")[0].get("revision").asInt())
        assertEquals(
            100,
            database()
                .queryForObject(
                    "select count(*) from expense_drafts where company_id=? and claim_id=?",
                    Int::class.java,
                    f.company,
                    f.claim,
                ),
        )
    }
}
