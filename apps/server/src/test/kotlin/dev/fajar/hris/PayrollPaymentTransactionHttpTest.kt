package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollPaymentTransactionHttpTest : PayrollPaymentApiFixture() {
    @Test
    fun competingReservationsAndReleaseDecisionsCommitOneCompleteTransition() {
        val f = paymentFixture()
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        val winner =
            Executors.newFixedThreadPool(2).use { pool ->
                val replies =
                    ids.map { id ->
                            pool.submit<HttpResponse<String>> {
                                preparePayrollPayment(f, id, listOf(instruction(f.ownedAssessment)))
                            }
                        }
                        .map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(listOf(200, 409), replies.map { it.statusCode() }.sorted())
                ids[replies.indexOfFirst { it.statusCode() == 200 }]
            }
        assertEquals(1, count(f.payroll.company, "payroll_payment_batches"))
        assertEquals(1, count(f.payroll.company, "payroll_payment_items"))
        assertEquals(1, progress(f)["version"].asLong())
        Executors.newFixedThreadPool(2).use { pool ->
            val replies =
                listOf("release", "cancel")
                    .map { action ->
                        pool.submit<HttpResponse<String>> {
                            payrollPaymentAction(f, winner, action, 0)
                        }
                    }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), replies.map { it.statusCode() }.sorted())
        }
        assertEquals(2, count(f.payroll.company, "payroll_payment_actions"))
        assertEquals(2, progress(f)["version"].asLong())
    }

    @Test
    fun failedPreparationAndReconciliationRollBackAllEvidenceReferencesAndSync() {
        val f = paymentFixture()
        val tables =
            listOf(
                "payroll_payment_batches",
                "payroll_payment_items",
                "payroll_payment_actions",
                "payroll_payment_results",
                "payroll_payment_heads",
                "bank_payment_references",
                "mobile_sync_changes",
                "audit_entries",
                "outbox_events",
                "operation_receipts",
            )
        val before = tables.associateWith { count(f.payroll.company, it) }
        val id = UUID.randomUUID()
        val item = UUID.randomUUID()
        val input = listOf(instruction(f.ownedAssessment, item))
        val prepareKey = UUID.randomUUID()
        paymentProbe.omitAction = true
        assertEquals(409, preparePayrollPayment(f, id, input, prepareKey).statusCode())
        paymentProbe.clear()
        assertEquals(before, tables.associateWith { count(f.payroll.company, it) })
        payrollBody(preparePayrollPayment(f, id, input, prepareKey))
        payrollBody(payrollPaymentAction(f, id, "release", 0))
        val pending = paymentView(f, id)
        val prior = tables.associateWith { count(f.payroll.company, it) }
        val key = UUID.randomUUID()
        val outcomes = listOf(result(item))
        for (omit in listOf(true, false)) {
            paymentProbe.omitResults = omit
            if (!omit)
                payrollProbe.beforeJournal = {
                    if (it.action == "payroll.payment_reconciled")
                        throw DataIntegrityViolationException("Owned payment rollback probe")
                }
            assertEquals(409, reconcilePayment(f, id, 1, outcomes, key).statusCode())
            paymentProbe.clear()
            payrollProbe.clear()
            assertEquals(prior, tables.associateWith { count(f.payroll.company, it) })
            assertEquals(pending, paymentView(f, id))
            assertEquals(2, progress(f)["version"].asLong())
        }
        payrollBody(reconcilePayment(f, id, 1, outcomes, key))
        assertEquals("CLOSED", paymentView(f, id)["status"].asString())
    }

    @Test
    fun duplicateBankReferencesCannotSettleDifferentAssessments() {
        val f = paymentFixture(twoEmployees = true)
        val batches =
            f.assessments.map { assessment ->
                val batch = UUID.randomUUID()
                val item = UUID.randomUUID()
                payrollBody(preparePayrollPayment(f, batch, listOf(instruction(assessment, item))))
                payrollBody(payrollPaymentAction(f, batch, "release", 0))
                batch to item
            }
        val reference = "BANK:${UUID.randomUUID()}"
        val at = clock.instant().plusMillis(1)
        clock.set(at)
        Executors.newFixedThreadPool(2).use { pool ->
            val replies =
                batches
                    .map { (batch, item) ->
                        pool.submit<HttpResponse<String>> {
                            reconcilePayment(
                                f,
                                batch,
                                1,
                                listOf(result(item, reference = reference, at = at)),
                            )
                        }
                    }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), replies.map { it.statusCode() }.sorted())
        }
        assertEquals(1, count(f.payroll.company, "bank_payment_references"))
        assertEquals(1, count(f.payroll.company, "payroll_payment_results"))
        assertEquals(
            listOf("CLOSED", "RELEASED"),
            batches.map { paymentView(f, it.first)["status"].asString() }.sorted(),
        )
    }

    @Test
    fun immutableEvidenceAndProjectionVersionRejectDirectEdits() {
        val f = paymentFixture()
        val id = UUID.randomUUID()
        val item = UUID.randomUUID()
        payrollBody(preparePayrollPayment(f, id, listOf(instruction(f.ownedAssessment, item))))
        payrollBody(payrollPaymentAction(f, id, "release", 0))
        payrollBody(reconcilePayment(f, id, 1, listOf(result(item))))
        for (sql in
            listOf(
                "update payroll_payment_items set amount=amount+1 where company_id=?",
                "update payroll_payment_heads set version=version+1 where company_id=?",
                "delete from payroll_payment_heads where company_id=?",
                "update bank_payment_references set transaction_reference='rewritten' where company_id=?",
                "delete from bank_payment_references where company_id=?",
                "delete from payroll_payment_results where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(sql, f.payroll.company)
        }
        val other = paymentFixture()
        val scoped =
            transactions.run(payrollActor(other.payroll, other.maker)) {
                Result.Success(
                    runtimeJdbc.queryForObject(
                        "select count(*) from payroll_payment_items where company_id=?",
                        Int::class.java,
                        f.payroll.company,
                    )
                )
            }
        assertEquals(Result.Success(0), scoped)
        payrollError(
            get(other.maker.client, "${other.path}/$id"),
            404,
            "payroll_payment_batch_not_found",
        )
    }

    @Test
    fun interruptedReconciliationCannotRetainTransfersOrLeakTheWorkerThread() {
        val f = paymentFixture()
        val id = UUID.randomUUID()
        val item = UUID.randomUUID()
        payrollBody(preparePayrollPayment(f, id, listOf(instruction(f.ownedAssessment, item))))
        payrollBody(payrollPaymentAction(f, id, "release", 0))
        val at = clock.instant().plusMillis(1)
        clock.set(at)
        val key = UUID.randomUUID()
        val outcomes =
            listOf(
                PayrollPaymentResult(
                    item,
                    PayrollPaymentItemStatus.SUCCEEDED,
                    "BANK:${UUID.randomUUID()}",
                    at,
                    "Confirmed statement",
                )
            )
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.payment_reconciled") {
                Thread.currentThread().interrupt()
                throw InterruptedException("Owned payment interruption")
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    reconcilePayroll.execute(
                        payrollActor(f.payroll, f.checker),
                        key,
                        id,
                        1,
                        outcomes,
                        "Statement verified",
                    )
                }
            val error =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(InterruptedException::class.java, error.cause)
            assertFalse(
                pool
                    .submit<Boolean> { Thread.currentThread().isInterrupted }
                    .get(5, TimeUnit.SECONDS)
            )
        }
        payrollProbe.clear()
        assertEquals(0, count(f.payroll.company, "bank_payment_references"))
        assertEquals(0, count(f.payroll.company, "payroll_payment_results"))
        assertEquals(2, progress(f)["version"].asLong())
        assertTrue(
            reconcilePayroll.execute(
                payrollActor(f.payroll, f.checker),
                key,
                id,
                1,
                outcomes,
                "Statement verified",
            ) is Result.Success
        )
    }
}
