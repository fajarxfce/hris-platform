package dev.fajar.hris.approvals.data.di

import dev.fajar.hris.approvals.data.datasources.*
import dev.fajar.hris.approvals.data.repositories.StoredApprovalRepository
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.approvals.domain.usecases.*
import dev.fajar.hris.core.domain.*
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
    ) =
        SaveApprovalTemplate(
            approvals,
            members,
            companies,
            identities,
            operations,
            journal,
            transactions,
        )

    @Bean
    fun listApprovalTemplates(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListApprovalTemplates(approvals, identities, transactions)

    @Bean
    fun listApprovalInbox(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListApprovalInbox(approvals, identities, transactions, clock)

    @Bean
    fun getApprovalRequest(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        members: MembershipRepository,
    ) = GetApprovalRequest(approvals, members, identities, transactions, clock)

    @Bean
    fun reassignApproval(
        approvals: ApprovalRepository,
        members: MembershipRepository,
        companies: CompanyRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) =
        ReassignApproval(
            approvals,
            members,
            companies,
            identities,
            operations,
            journal,
            transactions,
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
        )

    @Bean
    fun listMyDelegations(
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListMyDelegations(approvals, identities, transactions, clock)
}
