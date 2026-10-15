package dev.fajar.hris

import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPaymentRecoveryReviewHttpTest : PayrollAmendmentPublicationApiFixture() {
    @Test
    fun financeReviewCombinesCurrentBalanceRetainedReturnsAndExactVersionWithoutWriting() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val path = amendmentPath(scene.source) + "/recoveries/review"
        val counts = publicationCounts(scene)
        val before = payrollBody(get(bank.checker.client, path))
        assertEquals(counts, publicationCounts(scene))
        assertEquals(scene.source.assessment.toString(), before["assessmentId"].asString())
        assertEquals(currentProgress(scene), before["payments"])
        assertTrue(before["canConfirm"].asBoolean())
        assertTrue(before["blockers"].isEmpty)
        assertTrue(before["recoveries"].isEmpty)
        assertEquals(1, before["payments"]["settlement"]["assessmentRevision"].asLong())
        assertEquals(before["publishedAt"], before["earliestConfirmationAt"])

        val recovery = bankRecovery(scene, bank.checker, BigDecimal("100.00"))
        payrollBody(recordBankRecovery(scene, recovery))
        val after = payrollBody(get(bank.checker.client, path))
        assertTrue(after["canConfirm"].asBoolean())
        assertEquals(5, after["payments"]["version"].asLong())
        assertEquals("100.00", after["payments"]["settlement"]["confirmedRecovered"].asString())
        assertEquals(1, after["recoveries"].size())
        assertEquals(recovery.id.toString(), after["recoveries"][0]["id"].asString())
        assertEquals(4, after["recoveries"][0]["paymentVersion"].asLong())
        assertEquals(currentProgress(scene), after["payments"])

        payrollBody(recordBankRecovery(scene, bankRecovery(scene, bank.checker)))
        val settled = payrollBody(get(bank.checker.client, path))
        assertFalse(settled["canConfirm"].asBoolean())
        assertEquals(listOf("NOT_OVERPAID"), settled["blockers"].toList().map { it.asString() })
        assertEquals(2, settled["recoveries"].size())
    }

    @Test
    fun aReviewedReturnDoesNotReserveTheBalanceOrReplaceTheOriginalCommand() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val path = amendmentPath(scene.source) + "/recoveries/review"
        val before = payrollBody(get(bank.checker.client, path))
        val reviewed = bankRecovery(scene, bank.checker, BigDecimal("100.00"))
        val competing = bankRecovery(scene, bank.maker, BigDecimal("100.00"))
        val receipt = payrollBody(recordBankRecovery(scene, competing))
        payrollError(recordBankRecovery(scene, reviewed), 409, "payroll_payment_balance_changed")
        assertEquals(receipt, payrollBody(recordBankRecovery(scene, competing)))
        val after = payrollBody(get(bank.checker.client, path))
        assertEquals(
            before["payments"]["version"].asLong() + 1,
            after["payments"]["version"].asLong(),
        )
        assertEquals(1, after["recoveries"].size())
    }

    @Test
    fun beneficiaryCanReadAuthorizedEvidenceButCannotConfirmTheirOwnReturn() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val owner = scene.source.payroll.owner
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.pay')",
                scene.source.payroll.company,
                owner.account,
            )
        val path = amendmentPath(scene.source) + "/recoveries/review"
        val review = payrollBody(get(owner.client, path))
        assertFalse(review["canConfirm"].asBoolean())
        assertEquals(listOf("BENEFICIARY"), review["blockers"].toList().map { it.asString() })
        assertFalse(review.has("accountId"))
        payrollError(
            recordBankRecovery(scene, bankRecovery(scene, owner)),
            403,
            "payroll_self_recovery_forbidden",
        )
        payrollError(get(scene.source.maker.client, path), 403, "access_denied")
        val other = company(scene.source.payroll.admin, scene.source.payroll.adminCsrf)
        val stranger = payrollMember(other, setOf("company.read", "payroll.pay"))
        payrollError(get(stranger.client, path), 403, "company_access_denied")
        payrollError(
            get(
                stranger.client,
                "/api/v1/companies/$other/payroll/payslips/${scene.source.assessment}/recoveries/review",
            ),
            404,
            "payroll_payslip_not_found",
        )
    }

    @Test
    fun unpaidAndUnresolvedInstructionsExposeTheirSeparateBlockers() {
        val scene = publicationScene()
        val bank = bankInstruction(scene)
        val path = amendmentPath(scene.source) + "/recoveries/review"
        val unpaid = payrollBody(get(bank.checker.client, path))
        assertFalse(unpaid["canConfirm"].asBoolean())
        assertEquals(listOf("NOT_OVERPAID"), unpaid["blockers"].toList().map { it.asString() })
        payrollBody(prepareBank(scene, bank))
        val reserved = payrollBody(get(bank.checker.client, path))
        assertEquals(
            setOf("NOT_OVERPAID", "PENDING_PAYMENT"),
            reserved["blockers"].toList().map { it.asString() }.toSet(),
        )
        payrollBody(releaseBank(scene, bank))
        val pending = payrollBody(get(bank.checker.client, path))
        assertEquals(reserved["blockers"], pending["blockers"])
        payrollError(
            get(
                bank.checker.client,
                amendmentPath(scene.source)
                    .replace(scene.source.assessment.toString(), UUID.randomUUID().toString()) +
                    "/recoveries/review",
            ),
            404,
            "payroll_payslip_not_found",
        )
    }
}
