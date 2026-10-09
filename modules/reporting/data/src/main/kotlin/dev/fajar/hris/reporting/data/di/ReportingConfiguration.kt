package dev.fajar.hris.reporting.data.di

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.CompanyReadTransactionRunner
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.data.datasources.*
import dev.fajar.hris.reporting.data.repositories.StoredHeadcountReportRepository
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import dev.fajar.hris.reporting.domain.usecases.GetGroupHeadcountReport
import dev.fajar.hris.reporting.domain.usecases.GetHeadcountReport
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ReportingConfiguration {
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
