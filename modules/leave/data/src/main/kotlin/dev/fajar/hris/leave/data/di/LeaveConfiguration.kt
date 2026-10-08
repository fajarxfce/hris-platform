package dev.fajar.hris.leave.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.*
import dev.fajar.hris.leave.data.repositories.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
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
}
