package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PayrollPaymentBankReferenceHttpTest : PayrollPaymentApiFixture() {
    private data class Reimbursement(val batch: UUID, val item: UUID)

    // Build both real workflows in one company without bypassing business/storage invariants.
    private fun pendingReimbursement(f: PaymentFixture): Reimbursement {
        val p = f.payroll
        for ((member, permission) in
            listOf(
                p.owner to "expenses.self.manage",
                f.maker to "expenses.pay",
                f.checker to "expenses.pay",
            )) {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    p.company,
                    member.account,
                    permission,
                )
        }
        val reviewer = payrollMember(p.company, setOf("company.read", "expenses.approve"))
        val category = UUID.randomUUID()
        val base = "/api/v1/companies/${p.company}"
        payrollBody(
            command(
                p.admin,
                "$base/expenses/categories/$category",
                json.writeValueAsString(
                    mapOf(
                        "code" to "ALLOWANCE",
                        "name" to "Approved reimbursement",
                        "effectiveFrom" to "2026-01-01",
                        "maximumLineAmount" to "500000.00",
                        "maximumClaimAmount" to "2000000.00",
                        "receiptRequired" to false,
                        "costCenterRequired" to false,
                        "reason" to "Fictional payment reference fixture",
                    )
                ),
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                p.admin,
                "$base/approvals/templates/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "name" to "Reimbursement review",
                        "kind" to "EXPENSE",
                        "effectiveFrom" to "2026-01-01",
                        "stages" to
                            listOf(
                                mapOf(
                                    "assignment" to "NAMED",
                                    "accountIds" to listOf(reviewer.account),
                                )
                            ),
                        "reason" to "Independent review",
                    )
                ),
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val claim = UUID.randomUUID()
        payrollBody(
            command(
                p.owner.client,
                "$base/expenses/claims/$claim/draft",
                json.writeValueAsString(
                    mapOf(
                        "employmentId" to p.employee,
                        "title" to "Business reimbursement",
                        "description" to "Approved allowance",
                        "lines" to
                            listOf(
                                mapOf(
                                    "id" to UUID.randomUUID(),
                                    "categoryId" to category,
                                    "occurredOn" to
                                        LocalDate.now(clock.withZone(ZoneId.of("Asia/Jakarta")))
                                            .toString(),
                                    "amount" to "150000.00",
                                    "description" to "Business reimbursement",
                                    "receiptRevisionIds" to emptyList<UUID>(),
                                )
                            ),
                        "reason" to "Submit actual reimbursement",
                    )
                ),
                p.owner.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val submission = UUID.randomUUID()
        payrollBody(
            command(
                p.owner.client,
                "$base/expenses/claims/$claim/submit",
                json.writeValueAsString(
                    mapOf(
                        "submissionId" to submission,
                        "expectedVersion" to 0,
                        "reason" to "Employee submission",
                    )
                ),
                p.owner.csrf,
                UUID.randomUUID(),
            )
        )
        payrollBody(
            command(
                reviewer.client,
                "$base/expenses/submissions/$submission/decisions",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 1,
                        "expectedApprovalVersion" to 0,
                        "decision" to "APPROVE",
                        "reason" to "Expense reviewed independently",
                    )
                ),
                reviewer.csrf,
                UUID.randomUUID(),
            )
        )
        val batch = UUID.randomUUID()
        val item = UUID.randomUUID()
        payrollBody(
            command(
                f.maker.client,
                "$base/expenses/payments/$batch",
                json.writeValueAsString(
                    mapOf(
                        "title" to "Reimbursement payment",
                        "reason" to "Verified destination",
                        "items" to
                            listOf(
                                mapOf(
                                    "id" to item,
                                    "submissionId" to submission,
                                    "destination" to
                                        mapOf(
                                            "bankCode" to "014",
                                            "accountNumber" to "001234567890",
                                            "accountName" to "Verified recipient",
                                        ),
                                )
                            ),
                    )
                ),
                f.maker.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                f.checker.client,
                "$base/expenses/payments/$batch/release",
                json.writeValueAsString(
                    mapOf("expectedVersion" to 0, "reason" to "Independent release")
                ),
                f.checker.csrf,
                UUID.randomUUID(),
            )
        )
        return Reimbursement(batch, item)
    }

    private fun settleReimbursement(
        f: PaymentFixture,
        payment: Reimbursement,
        reference: String,
    ): HttpResponse<String> =
        command(
            f.checker.client,
            "/api/v1/companies/${f.payroll.company}/expenses/payments/${payment.batch}/reconcile",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to 1,
                    "results" to
                        listOf(result(payment.item, reference = reference, at = clock.instant())),
                    "reason" to "Bank statement confirmed",
                )
            ),
            f.checker.csrf,
            UUID.randomUUID(),
        )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun theSameTransferCannotSettlePayrollAndReimbursementRegardlessOfOrder(payrollFirst: Boolean) {
        val f = paymentFixture()
        val reimbursement = pendingReimbursement(f)
        val batch = UUID.randomUUID()
        val item = UUID.randomUUID()
        payrollBody(preparePayrollPayment(f, batch, listOf(instruction(f.ownedAssessment, item))))
        payrollBody(payrollPaymentAction(f, batch, "release", 0))
        clock.set(clock.instant().plusMillis(1))
        val reference = "BANK:${UUID.randomUUID()}"
        val salary = {
            reconcilePayment(
                f,
                batch,
                1,
                listOf(result(item, reference = reference, at = clock.instant())),
            )
        }
        if (payrollFirst) {
            payrollBody(salary())
            assertEquals(409, settleReimbursement(f, reimbursement, reference).statusCode())
            payrollBody(settleReimbursement(f, reimbursement, "BANK:${UUID.randomUUID()}"))
        } else {
            payrollBody(settleReimbursement(f, reimbursement, reference))
            assertEquals(409, salary().statusCode())
            assertEquals(2, progress(f)["version"].asLong())
            payrollBody(reconcilePayment(f, batch, 1, listOf(result(item))))
        }
        assertEquals(2, count(f.payroll.company, "bank_payment_references"))
        assertEquals(1, count(f.payroll.company, "payroll_payment_results"))
        assertEquals(1, count(f.payroll.company, "expense_payment_results"))
        assertEquals(3, progress(f)["version"].asLong())
    }

    @Test
    fun competingReimbursementsAndSalariesRetainOnlyOneSettlement() {
        val f = paymentFixture()
        val reimbursement = pendingReimbursement(f)
        val batch = UUID.randomUUID()
        val item = UUID.randomUUID()
        payrollBody(preparePayrollPayment(f, batch, listOf(instruction(f.ownedAssessment, item))))
        payrollBody(payrollPaymentAction(f, batch, "release", 0))
        clock.set(clock.instant().plusMillis(1))
        val reference = "BANK:${UUID.randomUUID()}"
        Executors.newFixedThreadPool(2).use { pool ->
            val salary =
                pool.submit<HttpResponse<String>> {
                    reconcilePayment(
                        f,
                        batch,
                        1,
                        listOf(result(item, reference = reference, at = clock.instant())),
                    )
                }
            val expense =
                pool.submit<HttpResponse<String>> {
                    settleReimbursement(f, reimbursement, reference)
                }
            assertEquals(
                listOf(200, 409),
                listOf(salary, expense).map { it.get(10, TimeUnit.SECONDS).statusCode() }.sorted(),
            )
        }
        assertEquals(1, count(f.payroll.company, "bank_payment_references"))
        assertEquals(
            1,
            count(f.payroll.company, "payroll_payment_results") +
                count(f.payroll.company, "expense_payment_results"),
        )
    }
}
