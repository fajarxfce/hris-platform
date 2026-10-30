package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class ExpenseDecisionDelegationCompletionTest : ExpenseCommandAssuranceApiFixture() {
    @Autowired private lateinit var originalClaims: ExpenseClaimRepository

    @ParameterizedTest
    @CsvSource(
        "approve,evidence",
        "reject,evidence",
        "return,evidence",
        "approve,journal",
        "reject,journal",
        "return,journal",
    )
    fun delegationMustAuthorizeTheCurrentStageThroughDecisionCompletion(
        action: String,
        stage: String,
    ) {
        val original = prepare(action)
        val f = original.fixture
        val permissions = setOf("company.read", "approvals.read", "expenses.approve")
        val recipient = expenseMember(f.company, permissions.toList())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                recipient,
            )
        val until = clock.instant().plusSeconds(1)
        val from = reviewer(f)
        val delegation = expenseDelegation(f, from, recipient, until = until)
        val scene =
            original.copy(
                actor = original.actor.copy(accountId = recipient, permissions = permissions)
            )
        var expire = true
        val observed =
            object : ExpenseClaimRepository by originalClaims {
                override fun duplicateReceiptDigests(
                    companyId: UUID,
                    exceptClaimId: UUID,
                    digests: Set<String>,
                ) =
                    originalClaims.duplicateReceiptDigests(companyId, exceptClaimId, digests).also {
                        if (expire && stage == "evidence" && companyId == f.company)
                            clock.set(until.plusSeconds(1))
                    }
            }
        commandProbe.afterJournal = {
            if (
                expire &&
                    stage == "journal" &&
                    it.companyId == f.company &&
                    it.action == "expenses.submission_reviewed"
            )
                clock.set(until.plusSeconds(1))
        }
        val before = snapshot(scene)
        try {
            val denied = invoke(scene, claimRepository = observed)
            assertTrue(denied is Result.Failed, "Expired delegation committed an expense decision")
            assertEquals("not_assigned_approver", (denied as Result.Failed).failure.code)
            assertEquals(before, snapshot(scene))
            expire = false
            commandProbe.clear()
            expenseDelegation(
                f,
                from,
                recipient,
                id = delegation,
                version = 0,
                until = clock.instant().plusSeconds(60),
            )
            val renewedBefore = snapshot(scene)
            val recovered = invoke(scene, claimRepository = observed)
            assertTrue(recovered is Result.Success, recovered.toString())
            assertFalse((recovered as Result.Success).value.replayed)
            val committed = snapshot(scene)
            assertEquals(
                (renewedBefore.getValue("operation_receipts") as Long) + 1,
                committed["operation_receipts"],
            )
            val replay = invoke(scene, claimRepository = observed)
            assertTrue(replay is Result.Success, replay.toString())
            assertTrue((replay as Result.Success).value.replayed)
            assertEquals(committed, snapshot(scene))
        } finally {
            commandProbe.clear()
            clock.set(Instant.now())
        }
    }
}
