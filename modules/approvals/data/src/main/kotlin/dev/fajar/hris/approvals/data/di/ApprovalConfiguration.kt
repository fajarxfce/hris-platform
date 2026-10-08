package dev.fajar.hris.approvals.data.di

import dev.fajar.hris.approvals.data.datasources.*
import dev.fajar.hris.approvals.data.repositories.StoredApprovalRepository
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.approvals.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
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
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SaveApprovalTemplate(approvals, members, operations, journal, transactions)

    @Bean
    fun listApprovalTemplates(approvals: ApprovalRepository, transactions: TransactionRunner) =
        ListApprovalTemplates(approvals, transactions)

    @Bean
    fun listApprovalInbox(
        approvals: ApprovalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListApprovalInbox(approvals, transactions, clock)

    @Bean
    fun getApprovalRequest(
        approvals: ApprovalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetApprovalRequest(approvals, transactions, clock)

    @Bean
    fun reassignApproval(
        approvals: ApprovalRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = ReassignApproval(approvals, members, operations, journal, transactions)

    @Bean
    fun saveApprovalDelegation(
        approvals: ApprovalRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = SaveApprovalDelegation(approvals, members, operations, journal, transactions, clock)

    @Bean
    fun listMyDelegations(
        approvals: ApprovalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListMyDelegations(approvals, transactions, clock)
}
