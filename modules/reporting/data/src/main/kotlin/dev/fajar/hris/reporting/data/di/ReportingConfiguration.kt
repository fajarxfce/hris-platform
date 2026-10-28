package dev.fajar.hris.reporting.data.di

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.CompanyReadTransactionRunner
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.data.datasources.*
import dev.fajar.hris.reporting.data.repositories.StoredAttendanceClosingReportRepository
import dev.fajar.hris.reporting.data.repositories.StoredAttendanceExceptionReportRepository
import dev.fajar.hris.reporting.data.repositories.StoredExpenseReportRepository
import dev.fajar.hris.reporting.data.repositories.StoredHeadcountReportRepository
import dev.fajar.hris.reporting.data.repositories.StoredLeaveReportRepository
import dev.fajar.hris.reporting.data.repositories.StoredOvertimeReportRepository
import dev.fajar.hris.reporting.data.repositories.StoredPayrollCostReportRepository
import dev.fajar.hris.reporting.domain.repositories.AttendanceClosingReportRepository
import dev.fajar.hris.reporting.domain.repositories.AttendanceExceptionReportRepository
import dev.fajar.hris.reporting.domain.repositories.ExpenseReportRepository
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import dev.fajar.hris.reporting.domain.repositories.LeaveReportRepository
import dev.fajar.hris.reporting.domain.repositories.OvertimeReportRepository
import dev.fajar.hris.reporting.domain.repositories.PayrollCostReportRepository
import dev.fajar.hris.reporting.domain.usecases.GetAttendanceClosingReport
import dev.fajar.hris.reporting.domain.usecases.GetAttendanceExceptionReport
import dev.fajar.hris.reporting.domain.usecases.GetExpenseReport
import dev.fajar.hris.reporting.domain.usecases.GetGroupHeadcountReport
import dev.fajar.hris.reporting.domain.usecases.GetHeadcountReport
import dev.fajar.hris.reporting.domain.usecases.GetLeaveReport
import dev.fajar.hris.reporting.domain.usecases.GetOvertimeReport
import dev.fajar.hris.reporting.domain.usecases.GetPayrollCostReport
import dev.fajar.hris.reporting.domain.usecases.ListExpenseReportClaims
import dev.fajar.hris.reporting.domain.usecases.ListLeaveReportRequests
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ReportingConfiguration {
    @Bean
    fun attendanceClosingReportSource(sql: DSLContext): AttendanceClosingReportDataSource =
        PostgresAttendanceClosingReportDataSource(sql)

    @Bean
    fun attendanceClosingReports(
        source: AttendanceClosingReportDataSource
    ): AttendanceClosingReportRepository = StoredAttendanceClosingReportRepository(source)

    @Bean
    fun getAttendanceClosingReport(
        reports: AttendanceClosingReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetAttendanceClosingReport(
            reports,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun payrollCostReportSource(sql: DSLContext): PayrollCostReportDataSource =
        PostgresPayrollCostReportDataSource(sql)

    @Bean
    fun payrollCostReports(source: PayrollCostReportDataSource): PayrollCostReportRepository =
        StoredPayrollCostReportRepository(source)

    @Bean
    fun getPayrollCostReport(
        reports: PayrollCostReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetPayrollCostReport(reports, companies, members, identities, transactions, security, clock)

    @Bean
    fun overtimeReportSource(sql: DSLContext): OvertimeReportDataSource =
        PostgresOvertimeReportDataSource(sql)

    @Bean
    fun overtimeReports(source: OvertimeReportDataSource): OvertimeReportRepository =
        StoredOvertimeReportRepository(source)

    @Bean
    fun getOvertimeReport(
        reports: OvertimeReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetOvertimeReport(reports, companies, members, identities, transactions, security, clock)

    @Bean
    fun expenseReportSource(sql: DSLContext): ExpenseReportDataSource =
        PostgresExpenseReportDataSource(sql)

    @Bean
    fun expenseReports(source: ExpenseReportDataSource): ExpenseReportRepository =
        StoredExpenseReportRepository(source)

    @Bean
    fun getExpenseReport(
        reports: ExpenseReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetExpenseReport(reports, companies, members, identities, transactions, security, clock)

    @Bean
    fun listExpenseReportClaims(
        reports: ExpenseReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListExpenseReportClaims(
            reports,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun leaveReportSource(sql: DSLContext): LeaveReportDataSource =
        PostgresLeaveReportDataSource(sql)

    @Bean
    fun leaveReports(source: LeaveReportDataSource): LeaveReportRepository =
        StoredLeaveReportRepository(source)

    @Bean
    fun getLeaveReport(
        reports: LeaveReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetLeaveReport(reports, companies, members, identities, transactions, security, clock)

    @Bean
    fun listLeaveReportRequests(
        reports: LeaveReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListLeaveReportRequests(
            reports,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun attendanceExceptionReportSource(sql: DSLContext): AttendanceExceptionReportDataSource =
        PostgresAttendanceExceptionReportDataSource(sql)

    @Bean
    fun attendanceExceptionReports(
        source: AttendanceExceptionReportDataSource
    ): AttendanceExceptionReportRepository = StoredAttendanceExceptionReportRepository(source)

    @Bean
    fun getAttendanceExceptionReport(
        reports: AttendanceExceptionReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetAttendanceExceptionReport(
            reports,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun headcountReportSource(sql: DSLContext): HeadcountReportDataSource =
        PostgresHeadcountReportDataSource(sql)

    @Bean
    fun headcountReports(source: HeadcountReportDataSource): HeadcountReportRepository =
        StoredHeadcountReportRepository(source)

    @Bean
    fun getHeadcountReport(
        reports: HeadcountReportRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetHeadcountReport(reports, companies, members, identities, transactions, clock)

    @Bean
    fun getGroupHeadcountReport(
        reports: HeadcountReportRepository,
        policies: CompanyClientPolicyRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        reads: CompanyReadTransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetGroupHeadcountReport(
            reports,
            policies,
            companies,
            members,
            identities,
            transactions,
            reads,
            security,
            clock,
        )
}
