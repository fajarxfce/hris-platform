package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ApprovalFenceProbeConfiguration::class, AccountLockProbeConfiguration::class)
class ExpenseReviewSecurityHttpTest : ExpenseReviewApiFixture() {
    @Autowired private lateinit var approvalProbe: ApprovalFenceProbe
    @Autowired private lateinit var accountProbe: AccountLockProbe

    @Test
    fun reassigningAnEarlierMakerOrDelegatingForThemCannotBypassIndependentReview() {
        val f = expenseFixture()
        expenseTemplate(f)
        val lines = readyLines(f)
        assertEquals(200, saveExpense(f, lines = lines).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.manage')",
                f.company,
                f.managerAccount,
            )
        val maker = reviewer(f)
        val edited =
            command(
                maker.browser,
                "${f.path}/${f.claim}/draft",
                expenseBody(f, 0, lines),
                maker.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, edited.statusCode(), edited.body())
        assertEquals(200, saveExpense(f, 1, lines = lines).statusCode())
        val id = UUID.randomUUID()
        assertEquals(200, submitExpense(f, id, 2).statusCode())
        val detail = submissionDetails(f, id)
        assertEquals("BLOCKED", detail.get("approval").get("status").asString())
        val approval = UUID.fromString(detail.get("approval").get("id").asString())
        val reassigned = reassignExpense(f, approval, setOf(maker.account))
        assertEquals(200, reassigned.statusCode(), reassigned.body())
        val direct = reviewExpense(f, id, maker, 3, 1)
        assertEquals(403, direct.statusCode(), direct.body())
        assertEquals("self_approval_denied", json.readTree(direct.body()).get("code").asString())
        val delegate =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        expenseDelegation(f, maker, delegate.account)
        val indirect = reviewExpense(f, id, delegate, 3, 1)
        assertEquals(403, indirect.statusCode(), indirect.body())
        assertEquals(0, submissionDetails(f, id).get("reviews").size())
        assertEquals(200, reassignExpense(f, approval, setOf(delegate.account), 1).statusCode())
        val independent = reviewExpense(f, id, delegate, 3, 2)
        assertEquals(200, independent.statusCode(), independent.body())
        assertEquals(
            delegate.account.toString(),
            submissionDetails(f, id).get("reviews")[0].get("decidingFor").asString(),
        )
    }

    @Test
    fun anAccountLinkedAfterSubmissionCannotApproveItsOwnExpenseOrDelegateTheDecision() {
        val original = expenseFixture()
        val f =
            original.copy(
                employee =
                    employee(
                        original.admin,
                        original.adminCsrf,
                        original.company,
                        manager = original.manager,
                    )
            )
        val beneficiary =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        val delegate =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        expenseTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to listOf(beneficiary.account),
                            )
                        )
                ),
        )
        val id = pendingExpense(f, asAdmin = true)
        assertTrue(submissionDetails(f, id, f.admin).get("requesterId").isNull)
        val linked =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to beneficiary.account,
                        "expectedVersion" to 0,
                        "reason" to "Identity verified independently",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, linked.statusCode(), linked.body())
        val direct = reviewExpense(f, id, beneficiary)
        assertEquals(403, direct.statusCode(), direct.body())
        assertEquals("self_approval_denied", json.readTree(direct.body()).get("code").asString())
        expenseDelegation(f, beneficiary, delegate.account)
        val indirect = reviewExpense(f, id, delegate)
        assertEquals(403, indirect.statusCode(), indirect.body())
        assertEquals("PENDING", submissionDetails(f, id, f.admin).get("claimStatus").asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from approval_decisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun anApprovalPermissionDoesNotGrantArbitraryClaimsOrCrossCompanyDecisions() {
        val f = expenseFixture()
        expenseTemplate(f)
        val id = pendingExpense(f)
        val admin = reviewer(f, adminAccount())
        val unrelated = reviewExpense(f, id, admin)
        assertEquals(403, unrelated.statusCode(), unrelated.body())
        val foreign = company(f.admin, f.adminCsrf)
        val scoped = reviewExpense(f.copy(company = foreign), id, admin)
        assertEquals(404, scoped.statusCode(), scoped.body())
        assertEquals("PENDING", submissionDetails(f, id).get("claimStatus").asString())
    }

    @Test
    fun delegationRevocationBeforeTheGuardRejectsBothDecisionAndLaterReceiptReplay() {
        val f = expenseFixture()
        expenseTemplate(f)
        val id = pendingExpense(f)
        val from = reviewer(f)
        val to =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        val delegation = expenseDelegation(f, from, to.account)
        val key = UUID.randomUUID()
        val barrier = ApprovalFenceProbe.Barrier(f.company)
        approvalProbe.beforeLock.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<java.net.http.HttpResponse<String>> {
                        reviewExpense(f, id, to, key = key)
                    }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                expenseDelegation(f, from, to.account, delegation, 0, false)
                barrier.release.countDown()
                val denied = pending.get(15, TimeUnit.SECONDS)
                assertEquals(403, denied.statusCode(), denied.body())
            }
        } finally {
            barrier.release.countDown()
            approvalProbe.beforeLock.set(null)
        }
        assertEquals(0, submissionDetails(f, id).get("reviews").size())
        expenseDelegation(f, from, to.account, delegation, 1, true)
        val accepted = reviewExpense(f, id, to, key = key)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(accepted.body(), reviewExpense(f, id, to, key = key).body())
        expenseDelegation(f, from, to.account, delegation, 2, false)
        assertEquals(403, reviewExpense(f, id, to, key = key).statusCode())
        assertEquals("APPROVED", submissionDetails(f, id).get("claimStatus").asString())
    }

    @Test
    fun aDecisionAlreadyInsideItsGuardFinishesBeforeConcurrentDelegationRevocation() {
        val f = expenseFixture()
        expenseTemplate(f)
        val id = pendingExpense(f)
        val from = reviewer(f)
        val to =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        val delegation = expenseDelegation(f, from, to.account)
        val barrier = ApprovalFenceProbe.Barrier(f.company)
        approvalProbe.beforeDecision.set(barrier)
        try {
            Executors.newFixedThreadPool(2).use { pool ->
                val deciding =
                    pool.submit<java.net.http.HttpResponse<String>> { reviewExpense(f, id, to) }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val observation = ApprovalFenceProbe.Observation(f.company)
                approvalProbe.observedLock.set(observation)
                val revoking =
                    pool.submit<UUID> {
                        expenseDelegation(f, from, to.account, delegation, 0, false)
                    }
                assertTrue(observation.entered.await(5, TimeUnit.SECONDS))
                assertFalse(revoking.isDone)
                barrier.release.countDown()
                val result = deciding.get(15, TimeUnit.SECONDS)
                assertEquals(200, result.statusCode(), result.body())
                assertEquals(delegation, revoking.get(15, TimeUnit.SECONDS))
            }
        } finally {
            barrier.release.countDown()
            approvalProbe.beforeDecision.set(null)
            approvalProbe.observedLock.set(null)
        }
        val detail = submissionDetails(f, id)
        assertEquals("APPROVED", detail.get("claimStatus").asString())
        assertEquals(
            from.account.toString(),
            detail.get("reviews")[0].get("decidingFor").asString(),
        )
        assertEquals(to.account.toString(), detail.get("reviews")[0].get("actorId").asString())
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select active from approval_delegations where company_id=? and id=?",
                    Boolean::class.java,
                    f.company,
                    delegation,
                ),
        )
    }

    @Test
    fun waitingDecisionsRecheckExpiryPermissionAndCredentialStateAfterAccountGuards() {
        for (mode in listOf("expiry", "permission", "credential")) {
            val f = expenseFixture()
            expenseTemplate(f)
            val id = pendingExpense(f)
            val from = reviewer(f)
            val to =
                reviewer(
                    f,
                    expenseMember(
                        f.company,
                        listOf("company.read", "approvals.read", "expenses.approve"),
                    ),
                )
            val until = clock.instant().plusSeconds(60)
            expenseDelegation(f, from, to.account, until = until)
            val barrier = AccountLockProbe.Barrier(minOf(from.account, to.account))
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<java.net.http.HttpResponse<String>> { reviewExpense(f, id, to) }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (mode) {
                        "expiry" -> clock.set(until.plusSeconds(1))
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.approve'",
                                    f.company,
                                    to.account,
                                )
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    to.account,
                                )
                    }
                    barrier.release.countDown()
                    val result = pending.get(15, TimeUnit.SECONDS)
                    assertEquals(
                        if (mode == "credential") 401 else 403,
                        result.statusCode(),
                        "$mode: ${result.body()}",
                    )
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
                clock.set(Instant.now())
            }
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from expense_reviews where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
            assertEquals("PENDING", submissionDetails(f, id).get("claimStatus").asString())
        }
    }
}
