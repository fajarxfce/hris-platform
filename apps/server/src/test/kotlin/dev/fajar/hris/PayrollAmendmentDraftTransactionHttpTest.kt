package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollAmendmentDraftTransactionHttpTest : PayrollAmendmentDraftApiFixture() {
    @Test
    fun missingRevisionOrAuditRollsBackCreationAndTheSameOperationCanBeRetried() {
        val f = amendmentFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val body = draftBody(f, amendmentPreparation(f))
        val before = draftCounts(f)
        draftProbe.omitRevision = true
        assertEquals(409, saveDraft(f, id, body, key).statusCode())
        draftProbe.clear()
        assertEquals(before, draftCounts(f))
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.amendment_saved")
                throw DataIntegrityViolationException("Owned draft journal failure")
        }
        assertEquals(409, saveDraft(f, id, body, key).statusCode())
        payrollProbe.clear()
        assertEquals(before, draftCounts(f))
        val receipt = payrollBody(saveDraft(f, id, body, key))
        assertEquals(0, receipt["version"].asLong())
        assertEquals(receipt, payrollBody(saveDraft(f, id, body, key)))
        assertEquals(
            before.getValue("payroll_amendments") + 1,
            count(f.payroll.company, "payroll_amendments"),
        )
    }

    @Test
    fun calculationHeaderResultsRevisionAuditAndReceiptAreOneAtomicOutcome() {
        val draft = draftFixture()
        val f = draft.source
        val original = draftView(draft)
        val before = draftCounts(f)
        val key = UUID.randomUUID()
        for (omission in listOf("revision", "calculation", "result", "journal")) {
            when (omission) {
                "revision" -> draftProbe.omitRevision = true
                "calculation" -> draftProbe.omitCalculation = true
                "result" -> draftProbe.omitResult = true
                "journal" -> payrollProbe.beforeJournal = {
                        if (it.action == "payroll.amendment_calculated")
                            throw DataIntegrityViolationException(
                                "Owned calculation journal failure"
                            )
                    }
            }
            val failed = draftCommand(draft, "calculate", 0, key)
            assertEquals(409, failed.statusCode(), "$omission: ${failed.body()}")
            draftProbe.clear()
            payrollProbe.clear()
            assertEquals(original, draftView(draft))
            assertEquals(before, draftCounts(f))
        }
        payrollBody(draftCommand(draft, "calculate", 0, key))
        assertEquals("CALCULATED", draftView(draft)["amendment"]["summary"]["status"].asString())
        val recorded = draftCounts(f)
        val cancelKey = UUID.randomUUID()
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.amendment_cancelled")
                throw DataIntegrityViolationException("Owned cancellation journal failure")
        }
        assertEquals(409, draftCommand(draft, "cancel", 1, cancelKey).statusCode())
        payrollProbe.clear()
        assertEquals(recorded, draftCounts(f))
        assertEquals("CALCULATED", draftView(draft)["amendment"]["summary"]["status"].asString())
        payrollBody(draftCommand(draft, "cancel", 1, cancelKey))
    }

    @Test
    fun databaseConstraintsRejectForgedActorsFactsOriginalsDifferencesAndIncompleteSuffixes() {
        val draft = draftFixture()
        val f = draft.source
        val before = draftCounts(f)
        val key = UUID.randomUUID()
        draftProbe.transformRevision = { it.apply { actorId = f.payroll.owner.account } }
        assertEquals(409, draftCommand(draft, "calculate", 0, key).statusCode())
        draftProbe.clear()
        assertEquals(before, draftCounts(f))
        for (corruption in listOf("ordinal", "facts", "original", "difference", "reference")) {
            draftProbe.transformResults = { rows ->
                rows.onEach { row ->
                    when (corruption) {
                        "ordinal" -> row.ordinal = row.ordinal + 1
                        "facts" -> row.facts = org.jooq.JSONB.valueOf("{}")
                        "original" -> row.original = org.jooq.JSONB.valueOf("{}")
                        "difference" ->
                            row.differenceTakeHome =
                                row.differenceTakeHome + java.math.BigDecimal.ONE
                        "reference" -> row.assessmentRevision = 1
                    }
                }
            }
            val failed = draftCommand(draft, "calculate", 0, key)
            assertEquals(409, failed.statusCode(), "$corruption: ${failed.body()}")
            draftProbe.clear()
            assertEquals(before, draftCounts(f))
        }
        payrollBody(draftCommand(draft, "calculate", 0, key))
        val calculationId = UUID.fromString(draftView(draft)["latestCalculation"]["id"].asString())
        for (statement in
            listOf(
                "update payroll_amendments set employee_name='Changed' where company_id=? and id=?",
                "delete from payroll_amendments where company_id=? and id=?",
                "update payroll_amendment_revisions set reason='Changed' where company_id=? and amendment_id=?",
                "delete from payroll_amendment_revisions where company_id=? and amendment_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, f.payroll.company, draft.id)
        }
        for (statement in
            listOf(
                "update payroll_amendment_calculations set assessment_count=0 where company_id=? and id=?",
                "delete from payroll_amendment_calculations where company_id=? and id=?",
                "update payroll_amendment_results set difference_take_home=0 where company_id=? and calculation_id=?",
                "delete from payroll_amendment_results where company_id=? and calculation_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, f.payroll.company, calculationId)
        }
    }

    @Test
    fun acquisitionAndMappingFailuresAreSanitizedAndNeverStoredAsCompletedRuleAttempts() {
        val draft = draftFixture()
        val before = draftCounts(draft.source)
        amendmentProbe.transform = { _, _ ->
            throw IllegalStateException("Owned source failure that must not reach the client")
        }
        val failed = draftCommand(draft, "calculate", 0)
        payrollError(failed, 500, "database_failure")
        assertFalse(failed.body().contains("Owned source failure"))
        amendmentProbe.clear()
        assertEquals(before, draftCounts(draft.source))
        payrollBody(draftCommand(draft, "calculate", 0))
        val id = draftView(draft)["latestCalculation"]["id"].asString()
        val path = draftsPath(draft.source) + "/${draft.id}/calculations/$id"
        val original = payrollBody(get(draft.source.maker.client, path))
        draftProbe.readResults = { it.dropLast(1) }
        payrollError(get(draft.source.maker.client, path), 500, "database_failure")
        draftProbe.clear()
        assertEquals(original, payrollBody(get(draft.source.maker.client, path)))
    }

    @Test
    fun interruptedDraftWritesRollbackAndDoNotLeakGuardsOrInterruptTheNextExecutorTask() {
        val draft = draftFixture()
        val actor = payrollActor(draft.source.payroll, draft.source.maker)
        val before = draftCounts(draft.source)
        val key = UUID.randomUUID()
        draftProbe.afterRevision = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Owned draft interruption")
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    calculateAmendment.execute(actor, key, draft.id, 0, "Reviewed remuneration")
                }
            val failure =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(InterruptedException::class.java, failure.cause)
            draftProbe.clear()
            assertEquals(before, draftCounts(draft.source))
            val retried =
                pool
                    .submit<Result<MutationReceipt>> {
                        assertFalse(Thread.currentThread().isInterrupted)
                        calculateAmendment.execute(actor, key, draft.id, 0, "Reviewed remuneration")
                    }
                    .get(10, TimeUnit.SECONDS)
            assertTrue(retried is Result.Success, retried.toString())
        }
        assertEquals("CALCULATED", draftView(draft)["amendment"]["summary"]["status"].asString())
    }
}
