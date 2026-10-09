package dev.fajar.hris

import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPaymentHttpTest : PayrollPaymentApiFixture() {
    @Test
    fun reviewedDestinationsAndFinalizedAmountsAreRetainedThroughReleaseAndSettlement() {
        val f = paymentFixture()
        val payables = payrollBody(get(f.maker.client, "${f.path}/payables?${dateRange()}"))
        assertEquals(1, payables["items"].size())
        assertEquals(f.ownedAssessment.toString(), payables["items"][0]["assessmentId"].asString())
        assertEquals(0, progress(f)["version"].asLong())
        val id = UUID.randomUUID()
        val item = UUID.randomUUID()
        val input = listOf(instruction(f.ownedAssessment, item, "=SUM(1,2)"))
        val key = UUID.randomUUID()
        val receipt = payrollBody(preparePayrollPayment(f, id, input, key))
        assertEquals(receipt, payrollBody(preparePayrollPayment(f, id, input, key)))
        val prepared = paymentView(f, id)
        assertEquals("PREPARED", prepared["status"].asString())
        assertEquals(payables["items"][0]["amount"], prepared["totalAmount"])
        assertEquals(
            "001234567890",
            prepared["items"][0]["destination"]["accountNumber"].asString(),
        )
        assertEquals(1, progress(f)["version"].asLong())
        payrollError(
            get(f.maker.client, "${f.path}/$id/export"),
            409,
            "payroll_payment_instructions_unavailable",
        )
        payrollError(
            payrollPaymentAction(f, id, "release", 0, f.maker),
            403,
            "independent_payment_release_required",
        )
        payrollBody(payrollPaymentAction(f, id, "release", 0))
        val export = get(f.checker.client, "${f.path}/$id/export")
        assertEquals(200, export.statusCode(), export.body())
        assertEquals(export.body(), get(f.checker.client, "${f.path}/$id/export").body())
        assertTrue(export.body().contains("\"'001234567890\""))
        assertTrue(export.body().contains("\"'=SUM(1,2)\""))
        assertTrue(export.body().contains(item.toString()))
        assertTrue(
            export.headers().firstValue("Content-Disposition").orElse("").contains("attachment")
        )
        assertTrue(export.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        payrollError(payrollPaymentAction(f, id, "cancel", 1), 409, "payroll_payment_not_prepared")
        val outcomes = listOf(result(item))
        val resultKey = UUID.randomUUID()
        val confirmed = payrollBody(reconcilePayment(f, id, 1, outcomes, resultKey))
        assertEquals(confirmed, payrollBody(reconcilePayment(f, id, 1, outcomes, resultKey)))
        assertEquals("CLOSED", paymentView(f, id)["status"].asString())
        assertEquals(3, progress(f)["version"].asLong())
        assertEquals("SUCCEEDED", progress(f)["attempts"][0]["status"].asString())
        assertFalse(progress(f).toString().contains("accountNumber"))
        assertFalse(progress(f).toString().contains("transactionReference"))
        assertEquals(1, count(f.payroll.company, "bank_payment_references"))
        assertEquals(
            0,
            payrollBody(get(f.maker.client, "${f.path}/payables?${dateRange()}"))["items"].size(),
        )
        payrollError(
            preparePayrollPayment(f, UUID.randomUUID(), listOf(instruction(f.ownedAssessment))),
            409,
            "payroll_assessment_payment_reserved",
        )
        payrollError(
            get(f.maker.client, "${f.path}/$id/export"),
            409,
            "payroll_payment_instructions_unavailable",
        )
        val history = payrollBody(get(f.maker.client, "${f.path}/$id/history"))
        assertEquals(3, history["items"].size())
        assertEquals(1, payrollBody(get(f.maker.client, "${f.path}/$id/results")).size())
        assertEquals(
            0,
            BigDecimal(prepared["totalAmount"].asString())
                .compareTo(BigDecimal(paymentView(f, id)["totalAmount"].asString())),
        )
    }

    @Test
    fun partialAndUnknownResultsNeverReleaseReservationsUntilNoTransferIsConfirmed() {
        val f = paymentFixture(twoEmployees = true)
        val id = UUID.randomUUID()
        val items = f.assessments.associateWith { UUID.randomUUID() }
        payrollBody(preparePayrollPayment(f, id, items.map { instruction(it.key, it.value) }))
        payrollBody(payrollPaymentAction(f, id, "release", 0))
        val first = items.entries.first()
        val second = items.entries.last()
        payrollBody(reconcilePayment(f, id, 1, listOf(result(first.value))))
        assertEquals("RELEASED", paymentView(f, id)["status"].asString())
        payrollError(
            get(f.maker.client, "${f.path}/$id/export"),
            409,
            "payroll_payment_instructions_unavailable",
        )
        payrollError(
            reconcilePayment(f, id, 2, listOf(result(second.value, false, confirmed = false))),
            422,
            "invalid_payroll_payment_result",
        )
        payrollError(
            preparePayrollPayment(f, UUID.randomUUID(), listOf(instruction(second.key))),
            409,
            "payroll_assessment_payment_reserved",
        )
        payrollBody(reconcilePayment(f, id, 2, listOf(result(second.value, false))))
        assertEquals("CLOSED", paymentView(f, id)["status"].asString())
        val retry = UUID.randomUUID()
        payrollBody(preparePayrollPayment(f, retry, listOf(instruction(second.key))))
        payrollBody(payrollPaymentAction(f, retry, "cancel", 0))
        assertEquals("CANCELLED", paymentView(f, retry)["status"].asString())
        assertEquals(3, count(f.payroll.company, "payroll_payment_items"))
        val available = payrollBody(get(f.maker.client, "${f.path}/payables?${dateRange()}"))
        assertEquals(
            listOf(second.key.toString()),
            available["items"]
                .iterator()
                .asSequence()
                .map { it["assessmentId"].asString() }
                .toList(),
        )
    }

    @Test
    fun payrollBeneficiariesMayParticipateWithAnIndependentAggregateReleaser() {
        val f = paymentFixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.pay')",
                f.payroll.company,
                f.payroll.owner.account,
            )
        val id = UUID.randomUUID()
        payrollBody(
            preparePayrollPayment(
                f,
                id,
                listOf(instruction(f.ownedAssessment)),
                member = f.payroll.owner,
            )
        )
        payrollError(
            payrollPaymentAction(f, id, "release", 0, f.payroll.owner),
            403,
            "independent_payment_release_required",
        )
        payrollBody(payrollPaymentAction(f, id, "release", 0, f.checker))
    }

    @Test
    fun commandsValidateBoundedInputsAndCannotSelectUnpublishedOrForeignAssessments() {
        val f = paymentFixture()
        val other = paymentFixture()
        val id = UUID.randomUUID()
        payrollError(
            preparePayrollPayment(f, id, listOf(instruction(other.ownedAssessment))),
            409,
            "payroll_assessment_not_payable",
        )
        payrollError(
            preparePayrollPayment(f, id, listOf(instruction(UUID.randomUUID()))),
            409,
            "payroll_assessment_not_payable",
        )
        payrollError(
            preparePayrollPayment(f, id, List(101) { instruction(UUID.randomUUID()) }),
            422,
            "invalid_payroll_payment_batch",
        )
        payrollError(
            preparePayrollPayment(
                f,
                id,
                listOf(instruction(f.ownedAssessment), instruction(f.ownedAssessment)),
            ),
            422,
            "invalid_payroll_payment_batch",
        )
        payrollError(
            preparePayrollPayment(
                f,
                id,
                listOf(instruction(f.ownedAssessment, accountName = "Bad\nname")),
            ),
            422,
            "invalid_payroll_payment_destination",
        )
        payrollError(
            preparePayrollPayment(
                f,
                id,
                listOf(instruction(f.ownedAssessment)),
                member = f.payroll.operator,
            ),
            403,
            "access_denied",
        )
        val key = UUID.randomUUID()
        val input = listOf(instruction(f.ownedAssessment))
        payrollBody(preparePayrollPayment(f, id, input, key))
        payrollError(
            preparePayrollPayment(f, id, listOf(instruction(f.ownedAssessment)), key),
            409,
            "operation_payload_mismatch",
        )
        payrollError(payrollPaymentAction(f, id, "release", 1), 409, "stale_version")
        assertEquals(
            422,
            get(f.maker.client, "${f.path}?from=2020-01-01&until=2026-12-31").statusCode(),
        )
        assertEquals(422, get(f.maker.client, "${f.path}?${dateRange()}&limit=201").statusCode())
    }
}
