package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePaymentRepository
import dev.fajar.hris.expenses.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, ExpensePaymentProbeConfiguration::class)
abstract class ExpensePaymentAssuranceApiFixture : ExpensePaymentApiFixture() {
    @Autowired private lateinit var payments: ExpensePaymentRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var paymentProbe: ExpensePaymentProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe

    protected data class PaymentScenario(
        val fixture: ExpenseFixture,
        val action: String,
        val security: IdentitySecurityPolicy,
        val actor: Actor,
        val batch: UUID,
        val item: UUID,
        val submission: UUID,
        val operation: UUID,
        val at: Instant,
        val reference: String,
    )

    @AfterEach
    fun releasePaymentAssuranceProbes() {
        paymentProbe.clear()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun preparePaymentScenario(action: String, mfa: Boolean): PaymentScenario {
        val f = expenseFixture()
        expenseTemplate(f)
        val submission = approvedExpense(f)
        val batch = UUID.randomUUID()
        val item = UUID.randomUUID()
        val maker = payer(f)
        val operator = if (action == "prepare") maker else payer(f)
        if (action !in setOf("prepare", "payables")) {
            val prepared =
                preparePayment(f, maker, batch, listOf(paymentInstruction(submission, item)))
            assertEquals(200, prepared.statusCode(), prepared.body())
        }
        if (action in setOf("export", "results", "reconcile")) {
            val released = paymentAction(f, operator, batch, "release", 0)
            assertEquals(200, released.statusCode(), released.body())
        }
        if (action == "results") {
            val reconciled = reconcilePayment(f, operator, batch, 1, listOf(paymentResult(item)))
            assertEquals(200, reconciled.statusCode(), reconciled.body())
        }
        clock.set(clock.instant().plusMillis(1))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                operator.account,
            )
        return PaymentScenario(
            f,
            action,
            IdentitySecurityPolicy(enforceMfa = mfa),
            Actor(
                operator.account,
                f.company,
                setOf("company.read", "expenses.pay"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant(),
                credentialVersion = 0,
            ),
            batch,
            item,
            submission,
            UUID.randomUUID(),
            clock.instant(),
            UUID.randomUUID().toString(),
        )
    }

    protected fun expiringActor(scene: PaymentScenario): Actor {
        val until = clock.instant().minus(scene.security.recentAuthenticationAge).plusSeconds(1)
        return if (scene.security.enforceMfa) scene.actor.copy(mfaVerifiedAt = until)
        else scene.actor.copy(authenticatedAt = until)
    }

    protected fun renewedActor(scene: PaymentScenario): Actor =
        scene.actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant())

    protected fun readPayment(scene: PaymentScenario, actor: Actor = scene.actor): Result<*> {
        val today = LocalDate.now(clock)
        return when (scene.action) {
            "batch" ->
                GetExpensePaymentBatch(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(actor, scene.batch)
            "export" ->
                GetExpensePaymentExport(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(actor, scene.batch)
            "history" ->
                GetExpensePaymentHistory(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(actor, scene.batch, null, 20)
            "results" ->
                GetExpensePaymentResults(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(actor, scene.batch)
            "batches" ->
                ListExpensePaymentBatches(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(actor, today.minusDays(1), today.plusDays(1), null, null, 20)
            "payables" ->
                ListExpensePayables(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(actor, today.minusDays(1), today.plusDays(1), null, 20)
            else -> error("Unknown payment read")
        }
    }

    protected fun commandPayment(
        scene: PaymentScenario,
        actor: Actor = scene.actor,
    ): Result<MutationReceipt> =
        when (scene.action) {
            "prepare" ->
                PrepareExpensePaymentBatch(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.batch,
                        "Independent reimbursement",
                        listOf(
                            ExpensePaymentInstruction(
                                scene.item,
                                scene.submission,
                                ExpensePaymentDestination("014", "001234567890", "Test Employee"),
                            )
                        ),
                        "Verified payment destinations",
                    )
            "release" ->
                ReleaseExpensePaymentBatch(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.batch,
                        0,
                        "Release verified instructions",
                    )
            "cancel" ->
                CancelExpensePaymentBatch(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.batch,
                        0,
                        "Replace unissued instructions",
                    )
            "reconcile" ->
                ReconcileExpensePaymentBatch(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.batch,
                        1,
                        listOf(
                            ExpensePaymentResult(
                                scene.item,
                                ExpensePaymentItemStatus.SUCCEEDED,
                                scene.reference,
                                scene.at,
                                "Verified bank settlement",
                            )
                        ),
                        "Bank statement reviewed",
                    )
            else -> error("Unknown payment command")
        }

    protected fun paymentSnapshot(scene: PaymentScenario): Map<String, Any> {
        val company = scene.fixture.company
        val tables =
            listOf(
                "expense_payment_batches",
                "expense_payment_items",
                "expense_payment_actions",
                "expense_payment_results",
                "bank_payment_references",
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
                "batchHeads" to
                    database()
                        .queryForList(
                            "select * from expense_payment_batches where company_id=? order by id",
                            company,
                        ),
                "itemHeads" to
                    database()
                        .queryForList(
                            "select * from expense_payment_items where company_id=? order by id",
                            company,
                        ),
                "claimHeads" to
                    database()
                        .queryForList(
                            "select id,version,status from expense_claims where company_id=? order by id",
                            company,
                        ),
            )
    }
}
