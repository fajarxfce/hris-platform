package dev.fajar.hris

import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentSettlementHttpTest : PayrollAmendmentPublicationApiFixture() {
    @Test
    fun settledSalaryAllowsOnlyTheReviewedPositiveDifferenceAndOriginalReceiptStillReplays() {
        val scene = publicationScene()
        val initial = bankInstruction(scene)
        val firstReceipt = payrollBody(prepareBank(scene, initial))
        payBank(scene, initial)
        val paid = currentProgress(scene)["settlement"]["confirmedPaid"].asString().toBigDecimal()
        publish(scene)
        val progress = currentProgress(scene)
        val delta = progress["settlement"]["payable"].asString().toBigDecimal()
        assertTrue(delta > BigDecimal.ZERO)
        assertEquals(4, progress["version"].asLong())
        assertEquals(firstReceipt, payrollBody(prepareBank(scene, initial)))
        val legacy = bankInstruction(scene)
        payrollError(prepareBank(scene, legacy), 409, "payroll_payment_observation_required")
        val correction = bankInstruction(scene, observed = true)
        payBank(scene, correction)
        val after = currentProgress(scene)
        assertEquals(0, after["settlement"]["payable"].asString().toBigDecimal().signum())
        assertEquals(
            0,
            after["settlement"]["confirmedPaid"].asString().toBigDecimal().compareTo(paid + delta),
        )
        assertEquals(7, after["version"].asLong())
        assertEquals(2, after["attempts"].size())
        assertEquals(
            setOf(0L, 1L),
            (0 until after["attempts"].size())
                .map { after["attempts"][it]["assessmentRevision"].asLong() }
                .toSet(),
        )
        assertEquals(2, count(scene.source.payroll.company, "bank_payment_references"))
    }

    @Test
    fun preparedAndPendingInstructionsPreventPublicationUntilResolved() {
        val scene = publicationScene()
        val bank = bankInstruction(scene)
        payrollBody(prepareBank(scene, bank))
        payrollError(admitPublication(scene), 409, "payroll_amendment_payment_pending")
        payrollBody(releaseBank(scene, bank))
        payrollError(admitPublication(scene), 409, "payroll_amendment_payment_pending")
        assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
        assertEquals(0, count(scene.source.payroll.company, "payroll_amendment_publications"))
    }

    @Test
    fun preparationAfterAdmissionIsDetectedAgainByTheWorker() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        val bank = bankInstruction(scene)
        payrollBody(prepareBank(scene, bank))
        val failure = stepPublication(scene, lease) as dev.fajar.hris.core.domain.Result.Failed
        assertEquals("payroll_amendment_payment_pending", failure.failure.code)
        assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
    }

    @Test
    fun confirmedRecoveryReducesOverpaymentWithoutCreatingNegativeTransfersAndReplaysExactly() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val before = currentProgress(scene)
        val amount = before["settlement"]["recoverable"].asString().toBigDecimal()
        assertTrue(amount > BigDecimal.ZERO)
        payrollError(
            prepareBank(scene, bankInstruction(scene)),
            409,
            "payroll_assessment_not_payable",
        )
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        clock.set(clock.instant().plusMillis(1))
        val body =
            json.writeValueAsString(
                mapOf(
                    "assessmentRevision" to 1,
                    "paymentVersion" to before["version"].asLong(),
                    "amount" to amount.toPlainString(),
                    "transactionReference" to "RETURN:${UUID.randomUUID()}",
                    "occurredAt" to clock.instant(),
                    "reason" to "Confirmed return to company account",
                )
            )
        val path = amendmentPath(scene.source) + "/recoveries/$id"
        val receipt =
            payrollBody(command(bank.checker.client, path, body, bank.checker.csrf, key, "PUT"))
        assertEquals(
            receipt,
            payrollBody(command(bank.checker.client, path, body, bank.checker.csrf, key, "PUT")),
        )
        val after = currentProgress(scene)
        assertEquals(5, after["version"].asLong())
        assertEquals(0, after["settlement"]["recoverable"].asString().toBigDecimal().signum())
        assertEquals(0, after["settlement"]["payable"].asString().toBigDecimal().signum())
        assertEquals(
            0,
            after["settlement"]["confirmedRecovered"].asString().toBigDecimal().compareTo(amount),
        )
        assertEquals(1, count(scene.source.payroll.company, "payroll_payment_items"))
        assertEquals(1, count(scene.source.payroll.company, "payroll_payment_recoveries"))
        assertEquals(
            1,
            payrollBody(get(bank.checker.client, amendmentPath(scene.source) + "/recoveries"))
                .size(),
        )
        assertEquals(2, count(scene.source.payroll.company, "bank_payment_references"))
    }

    @Test
    fun overRecoveryAndStaleBalanceObservationsProduceNoFinancialEvidence() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val view = currentProgress(scene)
        val amount = view["settlement"]["recoverable"].asString().toBigDecimal()
        clock.set(clock.instant().plusMillis(1))
        val base =
            mapOf(
                "assessmentRevision" to 1,
                "paymentVersion" to view["version"].asLong(),
                "amount" to amount.toPlainString(),
                "transactionReference" to "RETURN:${UUID.randomUUID()}",
                "occurredAt" to clock.instant(),
                "reason" to "Bank evidence",
            )
        val path = amendmentPath(scene.source) + "/recoveries/${UUID.randomUUID()}"
        payrollError(
            command(
                bank.checker.client,
                path,
                json.writeValueAsString(
                    base + ("amount" to (amount + BigDecimal.ONE).toPlainString())
                ),
                bank.checker.csrf,
                UUID.randomUUID(),
                "PUT",
            ),
            409,
            "payroll_payment_recovery_exceeds_balance",
        )
        payrollError(
            command(
                bank.checker.client,
                path,
                json.writeValueAsString(base + ("paymentVersion" to 0)),
                bank.checker.csrf,
                UUID.randomUUID(),
                "PUT",
            ),
            409,
            "payroll_payment_balance_changed",
        )
        assertEquals(0, count(scene.source.payroll.company, "payroll_payment_recoveries"))
        assertEquals(view, currentProgress(scene))
    }
}
