package dev.fajar.hris.leave.data.di

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.data.datasources.*
import dev.fajar.hris.leave.data.repositories.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class LeaveConfiguration {
    @Bean
    fun leaveEntitlementSource(sql: DSLContext): LeaveEntitlementDataSource =
        PostgresLeaveEntitlementDataSource(sql)

    @Bean
    fun leaveEntitlements(
        source: LeaveEntitlementDataSource,
        json: ObjectMapper,
    ): LeaveEntitlementRepository = StoredLeaveEntitlementRepository(source, json)

    @Bean
    fun postEmployeeLeaveAccrual(
        ledger: LeaveLedgerRepository,
        policies: LeavePolicyRepository,
        entitlements: LeaveEntitlementRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        PostEmployeeLeaveAccrual(
            ledger,
            policies,
            entitlements,
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
    fun closeEmployeeLeaveYear(
        ledger: LeaveLedgerRepository,
        policies: LeavePolicyRepository,
        entitlements: LeaveEntitlementRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        requests: LeaveRequestRepository,
    ) =
        CloseEmployeeLeaveYear(
            ledger,
            policies,
            entitlements,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            requests,
        )

    @Bean
    fun getLeaveAccount(
        ledger: LeaveLedgerRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetLeaveAccount(ledger, people, companies, members, identities, transactions, clock)

    @Bean
    fun getLeaveEntitlements(
        ledger: LeaveLedgerRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        entitlements: LeaveEntitlementRepository,
        policies: LeavePolicyRepository,
    ) =
        GetLeaveEntitlements(
            ledger,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            entitlements,
            policies,
        )

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
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
    ) = SaveLeaveType(policies, operations, journal, transactions, companies, members, identities)

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
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
    ) =
        AdjustLeaveBalance(
            ledger,
            policies,
            people,
            operations,
            journal,
            transactions,
            clock,
            companies,
            members,
            identities,
        )

    @Bean
    fun getLeaveLedger(
        ledger: LeaveLedgerRepository,
        policies: LeavePolicyRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
    ) =
        GetLeaveLedger(
            ledger,
            policies,
            people,
            transactions,
            clock,
            companies,
            members,
            identities,
        )

    @Bean
    fun leaveRequestSource(sql: DSLContext): LeaveRequestDataSource =
        PostgresLeaveRequestDataSource(sql)

    @Bean
    fun leaveAllocationSource(sql: DSLContext): LeaveAllocationDataSource =
        PostgresLeaveAllocationDataSource(sql)

    @Bean
    fun leaveAttachmentSource(sql: DSLContext): LeaveAttachmentDataSource =
        PostgresLeaveAttachmentDataSource(sql)

    @Bean
    fun leaveRequests(
        source: LeaveRequestDataSource,
        allocations: LeaveAllocationDataSource,
        attachments: LeaveAttachmentDataSource,
        json: ObjectMapper,
    ): LeaveRequestRepository = StoredLeaveRequestRepository(source, allocations, attachments, json)

    @Bean
    fun submitLeaveRequest(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        policies: LeavePolicyRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        schedules: ScheduleRepository,
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        documents: DocumentRepository,
        references: DocumentReferenceRepository,
    ) =
        SubmitLeaveRequest(
            requests,
            ledger,
            policies,
            people,
            companies,
            schedules,
            approvals,
            identities,
            members,
            operations,
            journal,
            transactions,
            clock,
            documents,
            references,
        )

    @Bean
    fun decideLeaveRequest(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        approvals: ApprovalRepository,
        identities: IdentityRepository,
        companies: CompanyRepository,
        people: PeopleRepository,
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
            identities,
            companies,
            people,
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
        identities: IdentityRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        WithdrawLeaveRequest(
            requests,
            ledger,
            approvals,
            identities,
            companies,
            members,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun requestLeaveCancellation(
        requests: LeaveRequestRepository,
        ledger: LeaveLedgerRepository,
        approvals: ApprovalRepository,
        identities: IdentityRepository,
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
            identities,
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
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        GetLeaveRequest(
            requests,
            ledger,
            people,
            approvals,
            members,
            identities,
            transactions,
            clock,
        )

    @Bean
    fun listLeaveRequests(
        requests: LeaveRequestRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListLeaveRequests(requests, people, transactions, clock)

    @Bean
    fun getLeaveAttachmentDownload(
        requests: LeaveRequestRepository,
        documents: DocumentRepository,
        people: PeopleRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        GetLeaveAttachmentDownload(
            requests,
            documents,
            people,
            approvals,
            companies,
            members,
            identities,
            transactions,
            clock,
        )

    @Bean
    fun readLeaveAttachmentContent(
        requests: LeaveRequestRepository,
        documents: DocumentRepository,
        people: PeopleRepository,
        approvals: ApprovalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        storage: ObjectStorageRepository,
    ) =
        ReadLeaveAttachmentContent(
            requests,
            documents,
            people,
            approvals,
            companies,
            members,
            identities,
            transactions,
            clock,
            storage,
        )
}
