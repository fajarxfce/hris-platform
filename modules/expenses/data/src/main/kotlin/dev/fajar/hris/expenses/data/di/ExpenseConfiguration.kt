package dev.fajar.hris.expenses.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.data.datasources.*
import dev.fajar.hris.expenses.data.repositories.StoredExpensePolicyRepository
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.expenses.domain.usecases.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
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
}
