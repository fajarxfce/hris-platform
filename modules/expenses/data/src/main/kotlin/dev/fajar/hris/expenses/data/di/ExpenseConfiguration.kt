package dev.fajar.hris.expenses.data.di

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.data.datasources.*
import dev.fajar.hris.expenses.data.repositories.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.expenses.domain.usecases.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExpenseConfiguration {
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
    ) =
        SaveExpenseCategory(
            policies,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
        )

    @Bean
    fun listExpenseCategories(
        policies: ExpensePolicyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListExpenseCategories(policies, identities, transactions)

    @Bean
    fun getExpenseCategoryHistory(
        policies: ExpensePolicyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetExpenseCategoryHistory(policies, identities, transactions)

    @Bean
    fun expenseClaimSource(sql: DSLContext): ExpenseClaimDataSource =
        PostgresExpenseClaimDataSource(sql)

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
        people: PeopleRepository,
        units: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        SaveExpenseDraft(
            claims,
            policies,
            documents,
            people,
            units,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
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
        )

    @Bean
    fun getExpenseClaim(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetExpenseClaim(claims, people, companies, identities, transactions, clock)

    @Bean
    fun getExpenseDrafts(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetExpenseDrafts(claims, people, companies, identities, transactions, clock)

    @Bean
    fun getExpenseClaimHistory(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetExpenseClaimHistory(claims, people, companies, identities, transactions, clock)

    @Bean
    fun listExpenseClaims(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListExpenseClaims(claims, people, companies, identities, transactions, clock)

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
        )

    @Bean
    fun listExpenseSubmissions(
        claims: ExpenseClaimRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListExpenseSubmissions(claims, people, companies, identities, transactions, clock)

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
        )
}
