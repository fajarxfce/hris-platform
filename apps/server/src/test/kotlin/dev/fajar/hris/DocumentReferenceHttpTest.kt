package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(DocumentReferenceProbeConfiguration::class)
class DocumentReferenceHttpTest : ExpenseApiFixture() {
    @Autowired private lateinit var references: DocumentReferenceRepository
    @Autowired private lateinit var scope: TransactionRunner
    @Autowired private lateinit var probe: DocumentReferenceProbe

    @AfterEach
    fun restoreReferenceWrites() {
        probe.skipWrites = false
    }

    private fun count(f: ExpenseFixture) =
        database()
            .queryForObject(
                "select count(*) from document_evidence_references where company_id=? and source_id=?",
                Int::class.java,
                f.company,
                f.claim,
            )

    @Test
    fun exactDraftEvidenceIsDeduplicatedRetainedAndCompanyScoped() {
        val f = expenseFixture()
        val receipt = expenseReceipt(f)
        val lines =
            listOf(
                expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))),
                expenseLine(
                    f,
                    mapOf("id" to UUID.randomUUID(), "receiptRevisionIds" to listOf(receipt)),
                ),
            )
        val operation = UUID.randomUUID()
        val first = saveExpense(f, lines = lines, key = operation)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(first.body(), saveExpense(f, lines = lines, key = operation).body())
        assertEquals(1, count(f))
        assertEquals(200, saveExpense(f, 0, lines = emptyList()).statusCode())
        assertEquals(200, cancelExpense(f, 1).statusCode())
        assertEquals(1, count(f))
        val actor = Actor(f.account, f.company, emptySet(), clock.instant(), UUID.randomUUID())
        assertEquals(
            Result.Success(setOf(receipt)),
            scope.run(actor) { references.referenced(f.company, setOf(receipt)) },
        )
        val other = expenseFixture()
        val foreign = actor.copy(accountId = other.account, companyId = other.company)
        assertEquals(
            Result.Success(emptySet<UUID>()),
            scope.run(foreign) { references.referenced(f.company, setOf(receipt)) },
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update("delete from document_evidence_references where company_id=?", f.company)
        }
    }

    @Test
    fun aMissingReferenceRollsBackTheBusinessCommitAndLeavesItsOperationRetryable() {
        val f = expenseFixture()
        val receipt = expenseReceipt(f)
        val lines = listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))))
        val operation = UUID.randomUUID()
        probe.skipWrites = true
        try {
            val result = saveExpense(f, lines = lines, key = operation)
            assertNotEquals(200, result.statusCode())
            assertEquals(0, count(f))
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from expense_claims where company_id=? and id=?",
                        Int::class.java,
                        f.company,
                        f.claim,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from audit_entries where resource_id=?",
                        Int::class.java,
                        f.claim,
                    ),
            )
        } finally {
            probe.skipWrites = false
        }
        val retry = saveExpense(f, lines = lines, key = operation)
        assertEquals(200, retry.statusCode(), retry.body())
        assertEquals(1, count(f))
    }

    @Test
    fun failedAuditRemovesTheReferenceAlongsideDraftAndReceiptRows() {
        val f = expenseFixture()
        val receipt = expenseReceipt(f)
        val lines = listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))))
        val operation = UUID.randomUUID()
        database()
            .execute(
                """create function fail_reference_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='${f.claim}'::uuid and new.action='expenses.draft_saved' then raise exception 'Fixture' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger reference_audit_probe before insert on audit_entries for each row execute function fail_reference_audit()"
            )
        try {
            assertNotEquals(200, saveExpense(f, lines = lines, key = operation).statusCode())
            assertEquals(0, count(f))
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from expense_draft_receipts where claim_id=?",
                        Int::class.java,
                        f.claim,
                    ),
            )
        } finally {
            database().execute("drop trigger reference_audit_probe on audit_entries")
            database().execute("drop function fail_reference_audit()")
        }
        assertEquals(200, saveExpense(f, lines = lines, key = operation).statusCode())
        assertEquals(1, count(f))
    }

    @Test
    fun competingLostResponsesCreateOneReferenceForTheCommittedDraft() {
        val f = expenseFixture()
        val receipt = expenseReceipt(f)
        val lines = listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))))
        val operation = UUID.randomUUID()
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val responses =
                (1..2).map {
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        saveExpense(f, lines = lines, key = operation)
                    }
                }
            gate.countDown()
            val results = responses.map { it.get(15, TimeUnit.SECONDS) }
            results.forEach { assertEquals(200, it.statusCode(), it.body()) }
            assertEquals(results[0].body(), results[1].body())
        }
        assertEquals(1, count(f))
    }
}
