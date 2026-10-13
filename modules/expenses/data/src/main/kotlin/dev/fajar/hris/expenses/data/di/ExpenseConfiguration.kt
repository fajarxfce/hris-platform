package dev.fajar.hris.expenses.data.di

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.data.datasources.*
import dev.fajar.hris.expenses.data.repositories.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.expenses.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExpenseConfiguration {
    @Bean
    fun getSelfExpenseDraftReferences(
        policies: ExpensePolicyRepository,
        people: PeopleRepository,
        units: OrganizationRepository,
        documents: DocumentRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
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

    @Bean
    fun expensePolicySource(sql: DSLContext): ExpensePolicyDataSource =
        PostgresExpensePolicyDataSource(sql)

    @Bean
    fun expensePolicies(source: ExpensePolicyDataSource): ExpensePolicyRepository =
        StoredExpensePolicyRepository(source)

    @Bean
    fun saveExpenseCategory(
        policies: ExpensePolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        SaveExpenseCategory(
            policies,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listExpenseCategories(
        policies: ExpensePolicyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        companies: CompanyRepository,
        members: MembershipRepository,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListExpenseCategories(
            policies,
            identities,
            transactions,
            companies,
            members,
            clock,
            security,
        )

    @Bean
    fun getExpenseCategoryHistory(
        policies: ExpensePolicyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        companies: CompanyRepository,
        members: MembershipRepository,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetExpenseCategoryHistory(
            policies,
            identities,
            transactions,
            companies,
            members,
            clock,
            security,
        )

    @Bean
    fun expenseClaimSource(sql: DSLContext): ExpenseClaimDataSource =
        PostgresExpenseClaimDataSource(sql)

    @Bean
    fun listExpensePolicies(
        policies: ExpensePolicyRepository,
        identities: IdentityRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = ListExpensePolicies(policies, identities, companies, members, transactions, clock, security)

    @Bean
    fun getExpensePolicy(
        policies: ExpensePolicyRepository,
        identities: IdentityRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = GetExpensePolicy(policies, identities, companies, members, transactions, clock, security)

    @Bean
    fun expenseSubmissionSource(sql: DSLContext): ExpenseSubmissionDataSource =
        PostgresExpenseSubmissionDataSource(sql)

    @Bean
    fun expenseClaims(
        source: ExpenseClaimDataSource,
        submissions: ExpenseSubmissionDataSource,
    ): ExpenseClaimRepository = StoredExpenseClaimRepository(source, submissions)

    @Bean
    fun saveExpenseDraft(
        claims: ExpenseClaimRepository,
        policies: ExpensePolicyRepository,
        documents: DocumentRepository,
        references: DocumentReferenceRepository,
        people: PeopleRepository,
        units: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun cancelExpenseDraft(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun listExpenseCostCenters(
        units: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListExpenseCostCenters(units, companies, members, identities, transactions, security, clock)

    @Bean
    fun listSelfExpenseEmployments(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListSelfExpenseEmployments(
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getExpenseClaimReview(
        claims: ExpenseClaimRepository,
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
        security: IdentitySecurityPolicy,
    ) =
        GetExpenseClaimReview(
            claims,
            payments,
            people,
            companies,
            identities,
            transactions,
            clock,
            members,
            security,
        )

    @Bean
    fun getExpenseClaim(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun getExpenseDrafts(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun getExpenseClaimHistory(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun listExpenseClaims(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun submitExpenseClaim(
        claims: ExpenseClaimRepository,
        policies: ExpensePolicyRepository,
        documents: DocumentRepository,
        people: PeopleRepository,
        units: OrganizationRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun withdrawExpenseSubmission(
        claims: ExpenseClaimRepository,
        approvals: ApprovalRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun getExpenseSubmission(
        claims: ExpenseClaimRepository,
        approvals: ApprovalRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun listExpenseSubmissions(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun reviewExpenseSubmission(
        claims: ExpenseClaimRepository,
        approvals: ApprovalRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ReviewExpenseSubmission(
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

    @Bean
    fun getExpenseReceiptDownload(
        claims: ExpenseClaimRepository,
        documents: DocumentRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        people: PeopleRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun readExpenseReceiptContent(
        claims: ExpenseClaimRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        people: PeopleRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
        documents: DocumentRepository,
        storage: ObjectStorageRepository,
        security: IdentitySecurityPolicy,
    ) =
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

    @Bean
    fun expensePaymentDataSource(sql: DSLContext): ExpensePaymentDataSource =
        PostgresExpensePaymentDataSource(sql)

    @Bean
    fun expensePaymentRepository(source: ExpensePaymentDataSource): ExpensePaymentRepository =
        StoredExpensePaymentRepository(source)

    @Bean
    fun prepareExpensePaymentBatch(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        PrepareExpensePaymentBatch(
            payments,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun releaseExpensePaymentBatch(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ReleaseExpensePaymentBatch(
            payments,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun cancelExpensePaymentBatch(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        CancelExpensePaymentBatch(
            payments,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun reconcileExpensePaymentBatch(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ReconcileExpensePaymentBatch(
            payments,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listExpensePayables(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListExpensePayables(
            payments,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listExpensePaymentBatches(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListExpensePaymentBatches(
            payments,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getExpensePaymentReview(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetExpensePaymentReview(
            payments,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getExpensePaymentBatch(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetExpensePaymentBatch(
            payments,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getExpensePaymentHistory(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetExpensePaymentHistory(
            payments,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getExpensePaymentResults(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetExpensePaymentResults(
            payments,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getExpensePaymentExport(
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetExpensePaymentExport(
            payments,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getExpenseClaimPayments(
        claims: ExpenseClaimRepository,
        payments: ExpensePaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
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
}
