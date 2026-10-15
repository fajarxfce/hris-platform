package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.data.datasources.LeaveLedgerDataSource
import dev.fajar.hris.leave.data.mappers.toCarryRow
import dev.fajar.hris.leave.data.models.LeaveBalanceRow
import dev.fajar.hris.leave.data.models.LeaveBalanceSummaryRow
import dev.fajar.hris.leave.data.repositories.StoredLeaveLedgerRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.leaveCarryValidity
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCarryBalanceMapperTest {
    private val company = UUID.randomUUID()
    private val employee = UUID.randomUUID()
    private val type = UUID.randomUUID()
    private val id = UUID.randomUUID()

    private fun row(): LeaveBalanceRow {
        val account =
            LeaveAccountsRecord().also {
                it.companyId = company
                it.id = id
                it.employmentId = employee
                it.typeId = type
                it.balanceYear = 2027
                it.availableHalfDays = 6L
                it.reservedHalfDays = 0L
                it.consumedHalfDays = 0L
                it.version = 1L
            }
        val carry =
            LeaveCarryAward(
                    LeaveCarryBucket(
                        UUID.randomUUID(),
                        2027,
                        6,
                        6,
                        validity =
                            (leaveCarryValidity(2027, 3, "Asia/Jakarta") as Result.Success).value,
                    ),
                    employee,
                    type,
                    "Asia/Jakarta",
                    3,
                )
                .toCarryRow(company)
        return LeaveBalanceRow(account, carry)
    }

    private fun repository(read: () -> LeaveBalanceRow) =
        StoredLeaveLedgerRepository(
            object : LeaveLedgerDataSource {
                override fun lock(company: UUID, employee: UUID, shared: Boolean) = Unit

                override fun balance(company: UUID, employee: UUID, type: UUID, year: Int) = read()

                override fun account(company: UUID, id: UUID) =
                    read().let {
                        LeaveBalanceSummaryRow(it.account, "ANNUAL", "Annual leave", it.carry)
                    }

                override fun list(
                    company: UUID,
                    employee: UUID,
                    year: Int,
                    after: String?,
                    limit: Int,
                ) = listOf(requireNotNull(account(company, id)))

                override fun entries(
                    company: UUID,
                    employee: UUID,
                    type: UUID,
                    year: Int,
                    after: UUID?,
                    limit: Int,
                ) = emptyList<LeaveLedgerRecord>()

                override fun append(rows: List<LeaveLedgerRecord>) =
                    error("Unexpected fixture write")
            }
        )

    @Test
    fun mutableRawValuesCannotRewriteAnyReadProjectionOrItsPersistedDeadline() {
        val raw = row()
        val carry = requireNotNull(raw.carry)
        carry.expiresAt = carry.expiresAt.plusHours(1)
        val repository = repository { raw }
        val balance = (repository.balance(company, employee, type, 2027) as Result.Success).value
        val account = (repository.account(company, id) as Result.Success).value
        val page = (repository.list(company, employee, 2027, null, 20) as Result.Success).value
        assertEquals(balance, account?.balance)
        assertEquals(balance, page.items.single().balance)
        assertEquals(carry.expiresAt.toInstant(), balance.carry?.bucket?.validity?.expiresAt)
        raw.account.availableHalfDays = 0L
        carry.availableHalfDays = 0
        carry.expiresAt = carry.expiresAt.plusDays(1)
        assertEquals(6, balance.availableHalfDays)
        assertEquals(6, balance.carry?.bucket?.availableHalfDays)
        assertEquals("2027-03-31T18:00:00Z", balance.carry?.bucket?.validity?.expiresAt.toString())
    }

    @Test
    fun foreignOrInconsistentBreakdownsFailInsideTheRepositoryBoundary() {
        val changes: List<(LeaveBalanceRow) -> Unit> =
            listOf(
                { it.account.companyId = UUID.randomUUID() },
                { it.account.employmentId = UUID.randomUUID() },
                { it.account.typeId = UUID.randomUUID() },
                { it.account.balanceYear = 2028 },
                { requireNotNull(it.carry).companyId = UUID.randomUUID() },
                { requireNotNull(it.carry).employmentId = UUID.randomUUID() },
                { requireNotNull(it.carry).typeId = UUID.randomUUID() },
                { requireNotNull(it.carry).availableHalfDays = 7 },
                { requireNotNull(it.carry).validThrough = null },
                { it.account.availableHalfDays = 5L },
                { it.account.closingId = UUID.randomUUID() },
            )
        for (change in changes) {
            val repository = repository { row().also(change) }
            assertTrue(repository.balance(company, employee, type, 2027) is Result.Failed)
            assertTrue(repository.account(company, id) is Result.Failed)
            assertTrue(repository.list(company, employee, 2027, null, 20) is Result.Failed)
        }
    }

    @Test
    fun legacyOrdinaryBalancesStayReadableAndCancellationPropagates() {
        val repository = repository { row().copy(carry = null) }
        assertNull(
            (repository.balance(company, employee, type, 2027) as Result.Success).value.carry
        )
        val cancelled = repository { throw CancellationException("fixture cancellation") }
        assertThrows(CancellationException::class.java) {
            cancelled.balance(company, employee, type, 2027)
        }
        assertThrows(CancellationException::class.java) { cancelled.account(company, id) }
        assertThrows(CancellationException::class.java) {
            cancelled.list(company, employee, 2027, null, 20)
        }
    }
}
