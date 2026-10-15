package dev.fajar.hris

import java.math.BigDecimal
import java.net.http.HttpResponse
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollAmendmentSettlementTransactionHttpTest : PayrollAmendmentPublicationApiFixture() {
    @Test
    fun failedRecoveryJournalRollsBackBankEvidencePaymentVersionAndSyncTogether() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val recovery = bankRecovery(scene, bank.checker)
        val before = publicationCounts(scene)
        val progress = currentProgress(scene)
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.payment_recovered")
                throw DataIntegrityViolationException("Owned recovery journal failure")
        }
        payrollError(recordBankRecovery(scene, recovery), 409, "data_conflict")
        payrollProbe.clear()
        assertEquals(before, publicationCounts(scene))
        assertEquals(progress, currentProgress(scene))
        assertEquals(1, count(scene.source.payroll.company, "bank_payment_references"))
        val receipt = payrollBody(recordBankRecovery(scene, recovery))
        assertEquals(receipt, payrollBody(recordBankRecovery(scene, recovery)))
        assertEquals(2, count(scene.source.payroll.company, "bank_payment_references"))
    }

    @Test
    fun concurrentIdenticalRecoveriesReturnOneReceiptAndDifferentObservationsCannotDoubleRecover() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val total = currentProgress(scene)["settlement"]["recoverable"].asString().toBigDecimal()
        val recovery = bankRecovery(scene, bank.checker, total.divide(BigDecimal("2")))
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2)
                    .map {
                        pool.submit<HttpResponse<String>> { recordBankRecovery(scene, recovery) }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(results[0], results[1])
            assertEquals(1, count(scene.source.payroll.company, "payroll_payment_recoveries"))
            val first = bankRecovery(scene, bank.checker)
            val second = bankRecovery(scene, bank.checker)
            val competing =
                listOf(first, second)
                    .map { command ->
                        pool.submit<HttpResponse<String>> { recordBankRecovery(scene, command) }
                    }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(
                listOf(200, 409),
                competing.map { it.statusCode() }.sorted(),
                competing.map { it.body() }.toString(),
            )
            payrollError(
                competing.single { it.statusCode() == 409 },
                409,
                "payroll_payment_balance_changed",
            )
        }
        assertEquals(2, count(scene.source.payroll.company, "payroll_payment_recoveries"))
        assertEquals(
            0,
            currentProgress(scene)["settlement"]["recoverable"].asString().toBigDecimal().signum(),
        )
    }

    @Test
    fun aTransferReferenceCannotAlsoBecomeRecoveryEvidenceAndConfirmedReturnsAreImmutable() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val reference =
            database()
                .queryForObject(
                    "select transaction_reference from payroll_payment_items where company_id=? and id=?",
                    String::class.java,
                    scene.source.payroll.company,
                    bank.item,
                )!!
        val before = currentProgress(scene)
        val duplicate = bankRecovery(scene, bank.checker, reference = reference)
        payrollError(recordBankRecovery(scene, duplicate), 409, "data_conflict")
        assertEquals(before, currentProgress(scene))
        assertEquals(0, count(scene.source.payroll.company, "payroll_payment_recoveries"))
        val recovery = bankRecovery(scene, bank.checker)
        payrollBody(recordBankRecovery(scene, recovery))
        for (statement in
            listOf(
                "update payroll_payment_recoveries set amount=1 where company_id=?",
                "delete from payroll_payment_recoveries where company_id=?",
                "delete from bank_payment_references where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, scene.source.payroll.company)
        }
    }

    @Test
    fun beneficiaryAndUnscopedAccountsCannotConfirmReturnsOrReadFinanceReferences() {
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
        payrollError(
            recordBankRecovery(scene, bankRecovery(scene, owner)),
            403,
            "payroll_self_recovery_forbidden",
        )
        payrollError(
            recordBankRecovery(scene, bankRecovery(scene, scene.source.maker)),
            403,
            "access_denied",
        )
        val otherCompany = company(scene.source.payroll.admin, scene.source.payroll.adminCsrf)
        val stranger = payrollMember(otherCompany, setOf("company.read", "payroll.pay"))
        payrollError(
            get(stranger.client, amendmentPath(scene.source) + "/recoveries"),
            403,
            "company_access_denied",
        )
        assertEquals(0, count(scene.source.payroll.company, "payroll_payment_recoveries"))
    }
}
