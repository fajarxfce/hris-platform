package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.entities.ExpenseDecision
import dev.fajar.hris.expenses.domain.entities.ExpenseLine
import dev.fajar.hris.expenses.domain.entities.SaveExpenseDraftCommand
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.expenses.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, ExpenseCommandProbeConfiguration::class)
abstract class ExpenseCommandAssuranceApiFixture : ExpenseReviewApiFixture() {
    @Autowired private lateinit var claims: ExpenseClaimRepository
    @Autowired private lateinit var policies: ExpensePolicyRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var references: DocumentReferenceRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var commandProbe: ExpenseCommandProbe
    protected val security = IdentitySecurityPolicy()

    protected data class Scenario(
        val fixture: ExpenseFixture,
        val action: String,
        val operation: UUID,
        val submission: UUID,
        val draft: SaveExpenseDraftCommand,
        val actor: Actor,
    )

    @AfterEach
    fun releaseExpenseCommandProbes() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        commandProbe.clear()
    }

    protected fun prepare(action: String): Scenario {
        val f = expenseFixture()
        val reviewing = action in setOf("approve", "reject", "return")
        val submission = UUID.randomUUID()
        val receipts =
            when (action) {
                "save",
                "edit" -> listOf(expenseReceipt(f))
                "submit",
                "withdraw",
                "approve",
                "reject",
                "return" -> listOf(readyReceipt(f))
                else -> emptyList()
            }
        if (action != "save") {
            val response =
                saveExpense(
                    f,
                    lines = listOf(expenseLine(f, mapOf("receiptRevisionIds" to receipts))),
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        if (action == "withdraw" || reviewing) {
            expenseTemplate(f)
            val response = submitExpense(f, submission)
            assertEquals(200, response.statusCode(), response.body())
        } else if (action == "submit") expenseTemplate(f)
        val account = if (reviewing) f.managerAccount else f.account
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        return Scenario(
            f,
            action,
            UUID.randomUUID(),
            submission,
            SaveExpenseDraftCommand(
                f.claim,
                f.employee,
                if (action == "save") null else 0,
                "Travel reimbursement",
                "Client visit",
                listOf(
                    ExpenseLine(
                        f.line,
                        f.category,
                        LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta")),
                        BigDecimal("150000.00"),
                        "Client visit",
                        f.costCenter,
                        receipts,
                    )
                ),
                "Retain expense evidence",
            ),
            Actor(
                account,
                f.company,
                if (reviewing) setOf("company.read", "expenses.team.approve")
                else setOf("company.read", "expenses.self.manage"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant(),
                credentialVersion = 0,
            ),
        )
    }

    protected fun invoke(
        scene: Scenario,
        actor: Actor = scene.actor,
        claimRepository: ExpenseClaimRepository = claims,
    ): Result<MutationReceipt> {
        val f = scene.fixture
        return when (scene.action) {
            "save",
            "edit" ->
                SaveExpenseDraft(
                        claims,
                        policies,
                        documents,
                        references,
                        people,
                        units,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.operation, scene.draft)
            "cancel" ->
                CancelExpenseDraft(
                        claims,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.operation, f.claim, 0, "Expense no longer required")
            "submit" ->
                SubmitExpenseClaim(
                        claims,
                        policies,
                        documents,
                        people,
                        units,
                        approvals,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        f.claim,
                        scene.submission,
                        0,
                        "Submit reimbursement",
                    )
            "withdraw" ->
                WithdrawExpenseSubmission(
                        claims,
                        approvals,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.operation, scene.submission, 1, "Correct expense details")
            "approve",
            "reject",
            "return" ->
                ReviewExpenseSubmission(
                        claimRepository,
                        approvals,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.submission,
                        1,
                        0,
                        ExpenseDecision.valueOf(scene.action.uppercase()),
                        "Independent expense review",
                        false,
                    )
            else -> error("Unknown expense command")
        }
    }

    protected fun snapshot(scene: Scenario): Map<String, Any> {
        val company = scene.fixture.company
        val tables =
            listOf(
                "expense_claims",
                "expense_drafts",
                "expense_draft_lines",
                "expense_draft_receipts",
                "document_evidence_references",
                "expense_claim_changes",
                "expense_submissions",
                "expense_submitted_lines",
                "expense_submitted_receipts",
                "expense_reviews",
                "approval_requests",
                "approval_decisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        return tables.associateWith { table ->
            database()
                .queryForObject(
                    "select count(*) from $table where company_id=?",
                    Long::class.java,
                    company,
                )!!
        } +
            mapOf(
                "claimHeads" to
                    database()
                        .queryForList(
                            "select id,version,status,draft_revision,submission_count,latest_submission_id from expense_claims where company_id=? order by id",
                            company,
                        ),
                "approvalHeads" to
                    database()
                        .queryForList(
                            "select id,version,status from approval_requests where company_id=? order by id",
                            company,
                        ),
            )
    }
}
