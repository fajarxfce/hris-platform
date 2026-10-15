package dev.fajar.hris.payroll.data.di

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
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
    fun payrollAmendmentReviewSource(sql: DSLContext): PayrollAmendmentReviewDataSource =
        PostgresPayrollAmendmentReviewDataSource(sql)

    @Bean
    fun payrollAmendmentReviews(
        source: PayrollAmendmentReviewDataSource
    ): PayrollAmendmentReviewRepository = StoredPayrollAmendmentReviewRepository(source)

    @Bean
    fun submitPayrollAmendmentReview(
        amendments: PayrollAmendmentRepository,
        reviews: PayrollAmendmentReviewRepository,
        sources: PayrollAmendmentSourceRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
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
        SubmitPayrollAmendmentReview(
            amendments,
            reviews,
            sources,
            policies,
            people,
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
    fun decidePayrollAmendmentReview(
        amendments: PayrollAmendmentRepository,
        reviews: PayrollAmendmentReviewRepository,
        sources: PayrollAmendmentSourceRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
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
        DecidePayrollAmendmentReview(
            amendments,
            reviews,
            sources,
            policies,
            people,
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
    fun withdrawPayrollAmendmentReview(
        amendments: PayrollAmendmentRepository,
        reviews: PayrollAmendmentReviewRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
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
        WithdrawPayrollAmendmentReview(
            amendments,
            reviews,
            policies,
            people,
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
    fun getPayrollAmendmentReview(
        amendments: PayrollAmendmentRepository,
        reviews: PayrollAmendmentReviewRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollAmendmentReview(
            amendments,
            reviews,
            policies,
            people,
            approvals,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollAmendmentReviews(
        amendments: PayrollAmendmentRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
        reviews: PayrollAmendmentReviewRepository,
    ) =
        ListPayrollAmendmentReviews(
            amendments,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
            reviews,
        )

    @Bean
    fun payrollAmendmentDraftSource(sql: DSLContext): PayrollAmendmentDataSource =
        PostgresPayrollAmendmentDataSource(sql)

    @Bean
    fun payrollAmendments(
        source: PayrollAmendmentDataSource,
        json: ObjectMapper,
    ): PayrollAmendmentRepository = StoredPayrollAmendmentRepository(source, json)

    @Bean
    fun savePayrollAmendment(
        amendments: PayrollAmendmentRepository,
        sources: PayrollAmendmentSourceRepository,
        policies: PayrollPolicyRepository,
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
        SavePayrollAmendment(
            amendments,
            sources,
            policies,
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
    fun calculatePayrollAmendment(
        amendments: PayrollAmendmentRepository,
        sources: PayrollAmendmentSourceRepository,
        policies: PayrollPolicyRepository,
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
        CalculatePayrollAmendment(
            amendments,
            sources,
            policies,
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
    fun cancelPayrollAmendment(
        amendments: PayrollAmendmentRepository,
        policies: PayrollPolicyRepository,
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
        CancelPayrollAmendment(
            amendments,
            policies,
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
    fun getPayrollAmendment(
        amendments: PayrollAmendmentRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
        reviews: PayrollAmendmentReviewRepository,
    ) =
        GetPayrollAmendment(
            amendments,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
            reviews,
        )

    @Bean
    fun getPayrollAmendmentHistory(
        amendments: PayrollAmendmentRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollAmendmentHistory(
            amendments,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollAmendmentRevision(
        amendments: PayrollAmendmentRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollAmendmentRevision(
            amendments,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollAmendmentCalculation(
        amendments: PayrollAmendmentRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollAmendmentCalculation(
            amendments,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollAmendments(
        amendments: PayrollAmendmentRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollAmendments(
            amendments,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun payrollAmendmentSource(sql: DSLContext): PayrollAmendmentSourceDataSource =
        PostgresPayrollAmendmentSourceDataSource(sql)

    @Bean
    fun payrollAmendmentSources(
        source: PayrollAmendmentSourceDataSource,
        json: ObjectMapper,
    ): PayrollAmendmentSourceRepository = StoredPayrollAmendmentSourceRepository(source, json)

    @Bean
    fun getPayrollAmendmentPreparation(
        policies: PayrollPolicyRepository,
        sources: PayrollAmendmentSourceRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollAmendmentPreparation(
            policies,
            sources,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun previewPayrollAmendment(
        policies: PayrollPolicyRepository,
        sources: PayrollAmendmentSourceRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        PreviewPayrollAmendment(
            policies,
            sources,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun payrollPolicySource(sql: DSLContext): PayrollPolicyDataSource =
        PostgresPayrollPolicyDataSource(sql)

    @Bean
    fun payrollEmployeeSource(sql: DSLContext): PayrollEmployeeDataSource =
        PostgresPayrollEmployeeDataSource(sql)

    @Bean
    fun compensationSource(sql: DSLContext): CompensationDataSource =
        PostgresCompensationDataSource(sql)

    @Bean
    fun payrollPolicies(
        source: PayrollPolicyDataSource,
        json: ObjectMapper,
    ): PayrollPolicyRepository = StoredPayrollPolicyRepository(source, json)

    @Bean
    fun compensations(
        source: CompensationDataSource,
        employees: PayrollEmployeeDataSource,
        json: ObjectMapper,
    ): CompensationRepository = StoredCompensationRepository(source, employees, json)

    @Bean
    fun getCompensationHistory(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetCompensationHistory(
            policies,
            compensations,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getEmployeeCompensation(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetEmployeeCompensation(
            policies,
            compensations,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollPolicy(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = GetPayrollPolicy(policies, companies, members, identities, transactions, clock, security)

    @Bean
    fun getPayrollPolicyReview(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollPolicyReview(
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollPolicyHistory(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollPolicyHistory(
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listEmployeeCompensations(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListEmployeeCompensations(
            policies,
            compensations,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getCompensationReview(
        policies: PayrollPolicyRepository,
        compensations: CompensationRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetCompensationReview(
            policies,
            compensations,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listCompensationEmployees(
        compensations: CompensationRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListCompensationEmployees(
            compensations,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollIncomeTaxRules(
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollIncomeTaxRules(
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

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
        cutoffs: PayrollCutoffRepository,
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
            cutoffs,
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
        cutoffs: PayrollCutoffRepository,
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
            cutoffs,
        )

    @Bean
    fun payrollTaxOpeningSource(sql: DSLContext): PayrollTaxOpeningDataSource =
        PostgresPayrollTaxOpeningDataSource(sql)

    @Bean
    fun payrollTaxOpenings(
        source: PayrollTaxOpeningDataSource,
        json: ObjectMapper,
        employees: PayrollEmployeeDataSource,
    ): PayrollTaxOpeningRepository = StoredPayrollTaxOpeningRepository(source, json, employees)

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
        cutoffs: PayrollCutoffRepository,
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
            cutoffs,
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
        cutoffs: PayrollCutoffRepository,
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
            cutoffs,
        )

    @Bean
    fun getPayrollTaxOpening(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollTaxOpening(
            openings,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollTaxOpeningHistory(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollTaxOpeningHistory(
            openings,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollTaxOpenings(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollTaxOpenings(
            openings,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollTaxOpeningReview(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        cutoffs: PayrollCutoffRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollTaxOpeningReview(
            openings,
            policies,
            cutoffs,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollTaxOpeningEmployees(
        openings: PayrollTaxOpeningRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollTaxOpeningEmployees(
            openings,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

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
    fun payrollPeriods(
        source: PayrollPeriodDataSource,
        employees: PayrollEmployeeDataSource,
    ): PayrollPeriodRepository = StoredPayrollPeriodRepository(source, employees)

    @Bean
    fun payrollInputs(
        source: PayrollInputDataSource,
        json: ObjectMapper,
        employees: PayrollEmployeeDataSource,
    ): PayrollInputRepository = StoredPayrollInputRepository(source, json, employees)

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
        cutoffs: PayrollCutoffRepository,
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
            cutoffs,
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
        cutoffs: PayrollCutoffRepository,
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
            cutoffs,
        )

    @Bean
    fun getPayrollPeriod(
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollPeriod(
            periods,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollPeriods(
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollPeriods(
            periods,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollPeriodEmployees(
        periods: PayrollPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollPeriodEmployees(
            periods,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollInputReview(
        inputs: PayrollInputRepository,
        sources: PayrollWorkSourceRepository,
        policies: PayrollPolicyRepository,
        cutoffs: PayrollCutoffRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollInputReview(
            inputs,
            sources,
            policies,
            cutoffs,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollInputEmployees(
        inputs: PayrollInputRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollInputEmployees(
            inputs,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollInput(
        inputs: PayrollInputRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollInput(
            inputs,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollInputHistory(
        inputs: PayrollInputRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollInputHistory(
            inputs,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollInputs(
        inputs: PayrollInputRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollInputs(
            inputs,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollWorkSource(
        sources: PayrollWorkSourceRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollWorkSource(
            sources,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun payrollRunSource(sql: DSLContext): PayrollRunDataSource = PostgresPayrollRunDataSource(sql)

    @Bean
    fun payrollRuns(source: PayrollRunDataSource, json: ObjectMapper): PayrollRunRepository =
        StoredPayrollRunRepository(source, json)

    @Bean
    fun payrollCalculationSource(sql: DSLContext): PayrollCalculationSourceDataSource =
        PostgresPayrollCalculationSourceDataSource(sql)

    @Bean
    fun payrollCalculationSources(
        source: PayrollCalculationSourceDataSource,
        json: ObjectMapper,
    ): PayrollCalculationSourceRepository = StoredPayrollCalculationSourceRepository(source, json)

    @Bean
    fun getPayrollCalculationReview(
        periods: PayrollPeriodRepository,
        runs: PayrollRunRepository,
        sources: PayrollCalculationSourceRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollCalculationReview(
            periods,
            runs,
            sources,
            policies,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun payrollCutoffSource(sql: DSLContext): PayrollCutoffDataSource =
        PostgresPayrollCutoffDataSource(sql)

    @Bean
    fun payrollCutoffs(source: PayrollCutoffDataSource): PayrollCutoffRepository =
        StoredPayrollCutoffRepository(source)

    @Bean
    fun startPayrollCalculation(
        runs: PayrollRunRepository,
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        sources: PayrollCalculationSourceRepository,
        operations: OperationRepository,
        security: IdentitySecurityPolicy,
    ) =
        StartPayrollCalculation(
            runs,
            periods,
            policies,
            people,
            companies,
            members,
            identities,
            jobs,
            journal,
            transactions,
            clock,
            sources,
            operations,
            security,
        )

    @Bean
    fun advancePayrollCalculation(
        runs: PayrollRunRepository,
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        sources: PayrollCalculationSourceRepository,
        compensation: CompensationRepository,
        inputs: PayrollInputRepository,
        openings: PayrollTaxOpeningRepository,
        assessments: PayrollAssessmentRepository,
    ) =
        AdvancePayrollCalculation(
            runs,
            periods,
            policies,
            people,
            companies,
            members,
            identities,
            jobs,
            journal,
            transactions,
            clock,
            sources,
            compensation,
            inputs,
            openings,
            assessments,
        )

    @Bean
    fun resumePayrollCalculation(
        runs: PayrollRunRepository,
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ResumePayrollCalculation(
            runs,
            periods,
            policies,
            people,
            companies,
            members,
            identities,
            jobs,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun abandonPayrollCalculation(
        runs: PayrollRunRepository,
        periods: PayrollPeriodRepository,
        policies: PayrollPolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
        reviews: PayrollReviewRepository,
    ) =
        AbandonPayrollCalculation(
            runs,
            periods,
            policies,
            people,
            companies,
            members,
            identities,
            jobs,
            operations,
            journal,
            transactions,
            security,
            clock,
            reviews,
        )

    @Bean
    fun abortPayrollCalculation(
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortPayrollCalculation(runs, policies, jobs, journal, transactions)

    @Bean
    fun getPayrollRun(
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        jobs: JobRepository,
        periods: PayrollPeriodRepository,
        reviews: PayrollReviewRepository,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollRun(
            runs,
            policies,
            companies,
            members,
            identities,
            transactions,
            jobs,
            periods,
            reviews,
            clock,
            security,
        )

    @Bean
    fun getPayrollRunEmployee(
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollRunEmployee(
            runs,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollRuns(
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        periods: PayrollPeriodRepository,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollRuns(
            runs,
            policies,
            companies,
            members,
            identities,
            transactions,
            periods,
            clock,
            security,
        )

    @Bean
    fun payrollReviewSource(sql: DSLContext): PayrollReviewDataSource =
        PostgresPayrollReviewDataSource(sql)

    @Bean
    fun payrollReviews(source: PayrollReviewDataSource): PayrollReviewRepository =
        StoredPayrollReviewRepository(source)

    @Bean
    fun submitPayrollReview(
        reviews: PayrollReviewRepository,
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SubmitPayrollReview(
            reviews,
            runs,
            policies,
            approvals,
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
    fun decidePayrollReview(
        reviews: PayrollReviewRepository,
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        DecidePayrollReview(
            reviews,
            runs,
            policies,
            approvals,
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
    fun withdrawPayrollReview(
        reviews: PayrollReviewRepository,
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
        finalizations: PayrollFinalizationRepository,
        jobs: JobRepository,
    ) =
        WithdrawPayrollReview(
            reviews,
            runs,
            policies,
            approvals,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
            finalizations,
            jobs,
        )

    @Bean
    fun getPayrollReview(
        reviews: PayrollReviewRepository,
        policies: PayrollPolicyRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        runs: PayrollRunRepository,
        finalizations: PayrollFinalizationRepository,
        jobs: JobRepository,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollReview(
            reviews,
            policies,
            approvals,
            companies,
            members,
            identities,
            transactions,
            runs,
            finalizations,
            jobs,
            clock,
            security,
        )

    @Bean
    fun listPayrollReviews(
        reviews: PayrollReviewRepository,
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollReviews(
            reviews,
            runs,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getPayrollReviewPreparation(
        reviews: PayrollReviewRepository,
        runs: PayrollRunRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollReviewPreparation(
            reviews,
            runs,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun payrollFinalizationSource(sql: DSLContext): PayrollFinalizationDataSource =
        PostgresPayrollFinalizationDataSource(sql)

    @Bean
    fun payrollFinalizations(source: PayrollFinalizationDataSource): PayrollFinalizationRepository =
        StoredPayrollFinalizationRepository(source)

    @Bean
    fun startPayrollFinalization(
        finalizations: PayrollFinalizationRepository,
        runs: PayrollRunRepository,
        reviews: PayrollReviewRepository,
        policies: PayrollPolicyRepository,
        approvals: ApprovalRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
        operations: OperationRepository,
    ) =
        StartPayrollFinalization(
            finalizations,
            runs,
            reviews,
            policies,
            approvals,
            people,
            companies,
            members,
            identities,
            jobs,
            journal,
            transactions,
            security,
            clock,
            operations,
        )

    @Bean
    fun advancePayrollFinalization(
        finalizations: PayrollFinalizationRepository,
        runs: PayrollRunRepository,
        reviews: PayrollReviewRepository,
        policies: PayrollPolicyRepository,
        approvals: ApprovalRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
        periods: PayrollPeriodRepository,
    ) =
        AdvancePayrollFinalization(
            finalizations,
            runs,
            reviews,
            policies,
            approvals,
            people,
            companies,
            members,
            identities,
            jobs,
            journal,
            transactions,
            security,
            clock,
            periods,
        )

    @Bean
    fun abortPayrollFinalization(
        finalizations: PayrollFinalizationRepository,
        policies: PayrollPolicyRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortPayrollFinalization(finalizations, policies, jobs, journal, transactions)

    @Bean
    fun getPayrollFinalization(
        finalizations: PayrollFinalizationRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        jobs: JobRepository,
        runs: PayrollRunRepository,
        reviews: PayrollReviewRepository,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollFinalization(
            finalizations,
            policies,
            companies,
            members,
            identities,
            transactions,
            jobs,
            runs,
            reviews,
            clock,
            security,
        )

    @Bean
    fun listPayrollFinalizations(
        finalizations: PayrollFinalizationRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        runs: PayrollRunRepository,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollFinalizations(
            finalizations,
            policies,
            companies,
            members,
            identities,
            transactions,
            runs,
            clock,
            security,
        )

    @Bean
    fun getPayrollFinalizationPreparation(
        finalizations: PayrollFinalizationRepository,
        runs: PayrollRunRepository,
        reviews: PayrollReviewRepository,
        policies: PayrollPolicyRepository,
        approvals: ApprovalRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollFinalizationPreparation(
            finalizations,
            runs,
            reviews,
            policies,
            approvals,
            people,
            companies,
            members,
            identities,
            jobs,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollPublicationRuns(
        finalizations: PayrollFinalizationRepository,
        policies: PayrollPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollPublicationRuns(
            finalizations,
            policies,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun payrollAssessmentSource(sql: DSLContext): PayrollAssessmentDataSource =
        PostgresPayrollAssessmentDataSource(sql)

    @Bean
    fun payrollAssessments(
        source: PayrollAssessmentDataSource,
        json: ObjectMapper,
    ): PayrollAssessmentRepository = StoredPayrollAssessmentRepository(source, json)

    @Bean
    fun getPayrollPayslip(
        assessments: PayrollAssessmentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollPayslip(
            assessments,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listPayrollPayslips(
        assessments: PayrollAssessmentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListPayrollPayslips(
            assessments,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun payrollPaymentSource(sql: DSLContext): PayrollPaymentDataSource =
        PostgresPayrollPaymentDataSource(sql)

    @Bean
    fun payrollPayments(source: PayrollPaymentDataSource): PayrollPaymentRepository =
        StoredPayrollPaymentRepository(source)

    @Bean
    fun getPayrollPaymentReview(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetPayrollPaymentReview(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun getPayrollPaymentBatch(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetPayrollPaymentBatch(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun getPayrollPaymentHistory(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetPayrollPaymentHistory(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun getPayrollPaymentResults(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetPayrollPaymentResults(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun getPayrollPaymentExport(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetPayrollPaymentExport(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun listPayrollPaymentBatches(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListPayrollPaymentBatches(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun listPayrollPayables(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListPayrollPayables(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun preparePayrollPaymentBatch(
        payments: PayrollPaymentRepository,
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
        PreparePayrollPaymentBatch(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            operations = operations,
            journal = journal,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun releasePayrollPaymentBatch(
        payments: PayrollPaymentRepository,
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
        ReleasePayrollPaymentBatch(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            operations = operations,
            journal = journal,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun cancelPayrollPaymentBatch(
        payments: PayrollPaymentRepository,
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
        CancelPayrollPaymentBatch(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            operations = operations,
            journal = journal,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun reconcilePayrollPaymentBatch(
        payments: PayrollPaymentRepository,
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
        ReconcilePayrollPaymentBatch(
            payments = payments,
            people = people,
            companies = companies,
            members = members,
            identities = identities,
            operations = operations,
            journal = journal,
            transactions = transactions,
            security = security,
            clock = clock,
        )

    @Bean
    fun getPayrollPayslipPayments(
        payments: PayrollPaymentRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetPayrollPayslipPayments(
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
