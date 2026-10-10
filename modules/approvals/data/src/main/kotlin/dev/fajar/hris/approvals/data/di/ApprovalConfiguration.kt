package dev.fajar.hris.approvals.data.di

import dev.fajar.hris.approvals.data.datasources.*
import dev.fajar.hris.approvals.data.repositories.StoredApprovalRepository
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.approvals.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class ApprovalConfiguration {
    @Bean
    fun approvalPolicies(sql: DSLContext): ApprovalPolicyDataSource =
        PostgresApprovalPolicyDataSource(sql)

    @Bean
    fun approvalRequests(sql: DSLContext): ApprovalRequestDataSource =
        PostgresApprovalRequestDataSource(sql)

    @Bean
    fun approvalDelegations(sql: DSLContext): DelegationDataSource =
        PostgresDelegationDataSource(sql)

    @Bean
    fun approvals(
        policies: ApprovalPolicyDataSource,
        requests: ApprovalRequestDataSource,
        delegations: DelegationDataSource,
        json: ObjectMapper,
    ): ApprovalRepository = StoredApprovalRepository(policies, requests, delegations, json)

    @Bean
    fun saveApprovalTemplate(
        approvals: ApprovalRepository,
        members: MembershipRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        SaveApprovalTemplate(
            approvals,
            members,
            companies,
            identities,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listApprovalTemplates(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = ListApprovalTemplates(approvals, identities, members, transactions, clock, security)

    @Bean
    fun listApprovalInbox(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = ListApprovalInbox(approvals, identities, members, transactions, clock, security)

    @Bean
    fun getApprovalRequest(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
        security: IdentitySecurityPolicy,
    ) = GetApprovalRequest(approvals, members, identities, transactions, clock, security)

    @Bean
    fun reassignApproval(
        approvals: ApprovalRepository,
        members: MembershipRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ReassignApproval(
            approvals,
            members,
            companies,
            identities,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun saveApprovalDelegation(
        approvals: ApprovalRepository,
        members: MembershipRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        SaveApprovalDelegation(
            approvals,
            members,
            companies,
            identities,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listMyDelegations(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = ListMyDelegations(approvals, identities, members, transactions, clock, security)
}
