package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired

abstract class PayrollPaymentAssuranceApiFixture : PayrollPaymentApiFixture() {
    @Autowired private lateinit var payments: PayrollPaymentRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    protected data class PaymentScenario(
        val fixture: PaymentFixture,
        val action: String,
        val security: IdentitySecurityPolicy,
        val actor: Actor,
        val batch: UUID,
        val item: UUID,
        val assessment: UUID,
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
        val f = paymentFixture()
        val batch = UUID.randomUUID()
        val item = UUID.randomUUID()
        val operator = if (action == "prepare") f.maker else f.checker
        if (action !in setOf("prepare", "payables")) {
            payrollBody(
                preparePayrollPayment(f, batch, listOf(instruction(f.ownedAssessment, item)))
            )
        }
        if (action in setOf("export", "results", "reconcile")) {
            payrollBody(payrollPaymentAction(f, batch, "release", 0))
        }
        if (action == "results") {
            payrollBody(reconcilePayment(f, batch, 1, listOf(result(item))))
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
                f.payroll.company,
                setOf("company.read", "payroll.pay"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant(),
                credentialVersion = 0,
            ),
            batch,
            item,
            f.ownedAssessment,
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
            "review" ->
                GetPayrollPaymentReview(
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
            "batch" ->
                GetPayrollPaymentBatch(
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
                GetPayrollPaymentExport(
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
                GetPayrollPaymentHistory(
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
                GetPayrollPaymentResults(
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
                ListPayrollPaymentBatches(
                        payments,
                        companies,
                        people,
                        members,
                        identities,
                        transactions,
                        scene.security,
                        clock,
                    )
                    .execute(actor, today.minusDays(1), today.plusDays(1), null, null, 20)
            "payables" ->
                ListPayrollPayables(
                        payments,
                        companies,
                        people,
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
                PreparePayrollPaymentBatch(
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
                        "Monthly payroll",
                        listOf(
                            PayrollPaymentInstruction(
                                scene.item,
                                scene.assessment,
                                PayrollPaymentDestination("014", "001234567890", "Test Employee"),
                            )
                        ),
                        "Verified payment destinations",
                    )
            "release" ->
                ReleasePayrollPaymentBatch(
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
                CancelPayrollPaymentBatch(
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
                ReconcilePayrollPaymentBatch(
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
                            PayrollPaymentResult(
                                scene.item,
                                PayrollPaymentItemStatus.SUCCEEDED,
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
        val company = scene.fixture.payroll.company
        val tables =
            listOf(
                "payroll_payment_batches",
                "payroll_payment_items",
                "payroll_payment_actions",
                "payroll_payment_results",
                "payroll_payment_heads",
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
                            "select * from payroll_payment_batches where company_id=? order by id",
                            company,
                        ),
                "itemHeads" to
                    database()
                        .queryForList(
                            "select * from payroll_payment_items where company_id=? order by id",
                            company,
                        ),
                "progressHeads" to
                    database()
                        .queryForList(
                            "select * from payroll_payment_heads where company_id=? order by assessment_id",
                            company,
                        ),
            )
    }
}
