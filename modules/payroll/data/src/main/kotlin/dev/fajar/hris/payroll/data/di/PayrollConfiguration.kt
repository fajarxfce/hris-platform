package dev.fajar.hris.payroll.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.payroll.data.repositories.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class PayrollConfiguration {
    @Bean
    fun payrollPolicySource(sql: DSLContext): PayrollPolicyDataSource =
        PostgresPayrollPolicyDataSource(sql)

    @Bean
    fun compensationSource(sql: DSLContext): CompensationDataSource =
        PostgresCompensationDataSource(sql)

    @Bean
    fun payrollPolicies(
        source: PayrollPolicyDataSource,
        json: ObjectMapper,
    ): PayrollPolicyRepository = StoredPayrollPolicyRepository(source, json)

    @Bean
    fun compensations(source: CompensationDataSource, json: ObjectMapper): CompensationRepository =
        StoredCompensationRepository(source, json)

    @Bean
    fun getCompensationHistory(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) =
        GetCompensationHistory(
            policies,
            compensations,
            companies,
            members,
            identities,
            transactions,
        )

    @Bean
    fun getEmployeeCompensation(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) =
        GetEmployeeCompensation(
            policies,
            compensations,
            companies,
            members,
            identities,
            transactions,
        )

    @Bean
    fun getPayrollPolicy(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetPayrollPolicy(policies, companies, members, identities, transactions)

    @Bean
    fun getPayrollPolicyHistory(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetPayrollPolicyHistory(policies, companies, members, identities, transactions)

    @Bean
    fun listEmployeeCompensations(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) =
        ListEmployeeCompensations(
            policies,
            compensations,
            companies,
            members,
            identities,
            transactions,
        )

    @Bean
    fun listPayrollIncomeTaxRules(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListPayrollIncomeTaxRules(policies, companies, members, identities, transactions)

    @Bean
    fun saveEmployeeCompensation(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
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
        SaveEmployeeCompensation(
            policies,
            compensations,
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
    fun savePayrollPolicy(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SavePayrollPolicy(
            policies,
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
    fun payrollTaxOpeningSource(sql: DSLContext): PayrollTaxOpeningDataSource =
        PostgresPayrollTaxOpeningDataSource(sql)

    @Bean
    fun payrollTaxOpenings(
        source: PayrollTaxOpeningDataSource,
        json: ObjectMapper,
    ): PayrollTaxOpeningRepository = StoredPayrollTaxOpeningRepository(source, json)

    @Bean
    fun savePayrollTaxOpening(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
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
        SavePayrollTaxOpening(
            openings,
            policies,
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
    fun verifyPayrollTaxOpening(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
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
        VerifyPayrollTaxOpening(
            openings,
            policies,
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
    fun getPayrollTaxOpening(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetPayrollTaxOpening(openings, policies, companies, members, identities, transactions)

    @Bean
    fun getPayrollTaxOpeningHistory(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) =
        GetPayrollTaxOpeningHistory(
            openings,
            policies,
            companies,
            members,
            identities,
            transactions,
        )

    @Bean
    fun listPayrollTaxOpenings(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListPayrollTaxOpenings(openings, policies, companies, members, identities, transactions)

    @Bean
    fun payrollPeriodSource(sql: DSLContext): PayrollPeriodDataSource =
        PostgresPayrollPeriodDataSource(sql)

    @Bean
    fun payrollInputSource(sql: DSLContext): PayrollInputDataSource =
        PostgresPayrollInputDataSource(sql)

    @Bean
    fun payrollWorkSource(sql: DSLContext): PayrollWorkSourceDataSource =
        PostgresPayrollWorkSourceDataSource(sql)

    @Bean
    fun payrollPeriods(source: PayrollPeriodDataSource): PayrollPeriodRepository =
        StoredPayrollPeriodRepository(source)

    @Bean
    fun payrollInputs(source: PayrollInputDataSource, json: ObjectMapper): PayrollInputRepository =
        StoredPayrollInputRepository(source, json)

    @Bean
    fun payrollWorkSources(source: PayrollWorkSourceDataSource): PayrollWorkSourceRepository =
        StoredPayrollWorkSourceRepository(source)

    @Bean
    fun createPayrollPeriod(
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
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
        CreatePayrollPeriod(
            periods,
            policies,
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
    fun cancelPayrollPeriod(
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        CancelPayrollPeriod(
            periods,
            policies,
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
    fun savePayrollInput(
        inputs: PayrollInputRepository,
        sources: PayrollWorkSourceRepository,
        policies: PayrollPolicyRepository,
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
        SavePayrollInput(
            inputs,
            sources,
            policies,
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
    fun verifyPayrollInput(
        inputs: PayrollInputRepository,
        sources: PayrollWorkSourceRepository,
        policies: PayrollPolicyRepository,
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
        VerifyPayrollInput(
            inputs,
            sources,
            policies,
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
    fun getPayrollPeriod(
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetPayrollPeriod(periods, policies, companies, members, identities, transactions)

    @Bean
    fun listPayrollPeriods(
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListPayrollPeriods(periods, policies, companies, members, identities, transactions)

    @Bean
    fun getPayrollInput(
        inputs: PayrollInputRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetPayrollInput(inputs, policies, companies, members, identities, transactions)

    @Bean
    fun getPayrollInputHistory(
        inputs: PayrollInputRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetPayrollInputHistory(inputs, policies, companies, members, identities, transactions)

    @Bean
    fun listPayrollInputs(
        inputs: PayrollInputRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListPayrollInputs(inputs, policies, companies, members, identities, transactions)

    @Bean
    fun getPayrollWorkSource(
        sources: PayrollWorkSourceRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetPayrollWorkSource(sources, policies, companies, members, identities, transactions)
}
