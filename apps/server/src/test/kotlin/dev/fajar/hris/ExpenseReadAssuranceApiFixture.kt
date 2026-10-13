package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.expenses.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, ExpenseReadProbeConfiguration::class)
abstract class ExpenseReadAssuranceApiFixture : ExpenseReviewApiFixture() {
    @Autowired private lateinit var claims: ExpenseClaimRepository
    @Autowired private lateinit var payments: ExpensePaymentRepository
    @Autowired private lateinit var policies: ExpensePolicyRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var objects: ObjectStorageRepository
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var readProbe: ExpenseReadProbe
    protected val security = IdentitySecurityPolicy()

    protected fun draftReferenceReader() =
        GetSelfExpenseDraftReferences(
            policies,
            people,
            units,
            documents,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    protected data class Scenario(
        val fixture: ExpenseFixture,
        val submission: UUID,
        val revision: UUID,
        val actor: Actor,
    ) {
        val company
            get() = fixture.company
    }

    @AfterEach
    fun releaseExpenseReadProbes() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        readProbe.current.getAndSet(null)?.release?.countDown()
        storageProbe.beforeRead = null
    }

    protected fun prepare(): Scenario {
        val f = expenseFixture()
        expenseTemplate(f)
        val revision = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, revision))
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.policy.manage')",
                f.company,
                f.account,
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        return Scenario(
            f,
            submission,
            revision,
            Actor(
                f.account,
                f.company,
                setOf("company.read", "expenses.self.manage", "expenses.policy.manage"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant(),
                credentialVersion = 0,
            ),
        )
    }

    protected fun acquireReadGuardsExclusively(scene: Scenario): Result<Unit> =
        transactions.run(scene.actor) {
            claims
                .lock(scene.company)
                .flatMap { people.lockReportingLines(scene.company) }
                .flatMap { approvals.lock(scene.company) }
                .flatMap { documents.lock(scene.company) }
                .flatMap { companies.lock(scene.company) }
                .flatMap { members.lock(scene.company) }
                .flatMap { identities.lockAccount(scene.actor.accountId) }
        }

    protected fun invoke(
        scene: Scenario,
        action: String,
        actor: Actor = scene.actor,
        storage: ObjectStorageRepository = objects,
    ): Result<*> {
        val f = scene.fixture
        val scope = ExpenseClaimReadScope.SELF
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        return when (action) {
            "list" ->
                ListExpenseClaims(
                        claims,
                        people,
                        companies,
                        identities,
                        transactions,
                        clock,
                        members,
                        security,
                    )
                    .execute(actor, f.employee, null, today, today, null, 20, scope)
            "details" ->
                GetExpenseClaim(
                        claims,
                        people,
                        companies,
                        identities,
                        transactions,
                        clock,
                        members,
                        security,
                    )
                    .execute(actor, f.claim, scope)
            "drafts" ->
                GetExpenseDrafts(
                        claims,
                        people,
                        companies,
                        identities,
                        transactions,
                        clock,
                        members,
                        security,
                    )
                    .execute(actor, f.claim, null, 20, scope)
            "history" ->
                GetExpenseClaimHistory(
                        claims,
                        people,
                        companies,
                        identities,
                        transactions,
                        clock,
                        members,
                        security,
                    )
                    .execute(actor, f.claim, null, 20, scope)
            "submissions" ->
                ListExpenseSubmissions(
                        claims,
                        people,
                        companies,
                        identities,
                        transactions,
                        clock,
                        members,
                        security,
                    )
                    .execute(actor, f.claim, null, 20, scope)
            "payments" ->
                GetExpenseClaimPayments(
                        claims,
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, f.claim, scope)
            "submission" ->
                GetExpenseSubmission(
                        claims,
                        approvals,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.submission, scope)
            "categories" ->
                ListExpenseCategories(
                        policies,
                        identities,
                        transactions,
                        companies,
                        members,
                        clock,
                        security,
                    )
                    .execute(actor, today, null, 20)
            "category-history" ->
                GetExpenseCategoryHistory(
                        policies,
                        identities,
                        transactions,
                        companies,
                        members,
                        clock,
                        security,
                    )
                    .execute(actor, f.category, null, 20)
            "policy-catalog" ->
                ListExpensePolicies(
                        policies,
                        identities,
                        companies,
                        members,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, null, null, 20)
            "policy-review" ->
                GetExpensePolicy(
                        policies,
                        identities,
                        companies,
                        members,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, f.category, null, 20)
            "directory" ->
                ListSelfExpenseEmployments(
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, null, 20)
            "receipt-metadata" ->
                GetExpenseReceiptDownload(
                        claims,
                        documents,
                        approvals,
                        companies,
                        people,
                        identities,
                        members,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.submission, scene.revision, scope)
            "receipt-content" -> {
                storageProbe.beforeRead = { readProbe.afterRead(f.company, action) }
                ReadExpenseReceiptContent(
                        claims,
                        documents,
                        approvals,
                        companies,
                        people,
                        identities,
                        members,
                        transactions,
                        clock,
                        storage,
                        security,
                    )
                    .execute(actor, scene.submission, scene.revision, 0, pdf.size, scope)
            }
            else -> error("Unknown expense read")
        }
    }
}
