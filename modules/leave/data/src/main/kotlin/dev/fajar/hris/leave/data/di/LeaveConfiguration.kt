package dev.fajar.hris.leave.data.di

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.data.datasources.*
import dev.fajar.hris.leave.data.repositories.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class LeaveConfiguration {
    @Bean
    fun leavePolicySource(sql: DSLContext): LeavePolicyDataSource =
        PostgresLeavePolicyDataSource(sql)

    @Bean
    fun leaveLedgerSource(sql: DSLContext): LeaveLedgerDataSource =
        PostgresLeaveLedgerDataSource(sql)

    @Bean
    fun leavePolicies(source: LeavePolicyDataSource, json: ObjectMapper): LeavePolicyRepository =
        StoredLeavePolicyRepository(source, json)

    @Bean
    fun leaveLedger(source: LeaveLedgerDataSource): LeaveLedgerRepository =
        StoredLeaveLedgerRepository(source)

    @Bean
    fun saveLeaveType(
        policies: LeavePolicyRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SaveLeaveType(policies, operations, journal, transactions)

    @Bean
    fun listLeaveTypes(policies: LeavePolicyRepository, transactions: TransactionRunner) =
        ListLeaveTypes(policies, transactions)

    @Bean
    fun adjustLeaveBalance(
        ledger: LeaveLedgerRepository,
        policies: LeavePolicyRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = AdjustLeaveBalance(ledger, policies, people, operations, journal, transactions, clock)

    @Bean
    fun getLeaveLedger(
        ledger: LeaveLedgerRepository,
        policies: LeavePolicyRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetLeaveLedger(ledger, policies, people, transactions, clock)

    @Bean
    fun leaveRequestSource(sql: DSLContext): LeaveRequestDataSource =
        PostgresLeaveRequestDataSource(sql)

    @Bean
    fun leaveAllocationSource(sql: DSLContext): LeaveAllocationDataSource =
        PostgresLeaveAllocationDataSource(sql)

    @Bean
    fun leaveRequests(
        source: LeaveRequestDataSource,
        allocations: LeaveAllocationDataSource,
        json: ObjectMapper,
    ): LeaveRequestRepository = StoredLeaveRequestRepository(source, allocations, json)

    @Bean
    fun submitLeaveRequest(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        policies: LeavePolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        schedules: ScheduleRepository,
        approvals: ApprovalRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        SubmitLeaveRequest(
            requests,
            ledger,
            policies,
            people,
            companies,
            schedules,
            approvals,
            members,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun decideLeaveRequest(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        approvals: ApprovalRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        DecideLeaveRequest(
            requests,
            ledger,
            approvals,
            members,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun withdrawLeaveRequest(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        approvals: ApprovalRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = WithdrawLeaveRequest(requests, ledger, approvals, operations, journal, transactions, clock)

    @Bean
    fun requestLeaveCancellation(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        approvals: ApprovalRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        RequestLeaveCancellation(
            requests,
            ledger,
            approvals,
            people,
            companies,
            members,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun getLeaveRequest(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        people: PeopleRepository,
        approvals: ApprovalRepository,
        members: MembershipRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetLeaveRequest(requests, ledger, people, approvals, members, transactions, clock)

    @Bean
    fun listLeaveRequests(
        requests: LeaveRequestRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListLeaveRequests(requests, people, transactions, clock)
}
