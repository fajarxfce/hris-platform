package dev.fajar.hris.payroll.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.data.datasources.PayrollComparisonDataSource
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.data.repositories.StoredPayrollComparisonRepository
import dev.fajar.hris.payroll.domain.entities.PayrollComparisonChange
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollComparisonRepositoryTest {
    private val company = UUID.randomUUID()
    private val baseline = UUID.randomUUID()
    private val current = UUID.randomUUID()
    private val one = UUID.fromString("10000000-0000-4000-8000-000000000001")
    private val two = UUID.fromString("f0000000-0000-4000-8000-000000000002")

    private fun money(g: String, w: String, h: String) =
        PayrollComparisonAmountsRow(BigDecimal(g), BigDecimal(w), BigDecimal(h))

    private val amounts = money("10.01", "-0.01", "10.02")

    private fun employee(m: PayrollComparisonAmountsRow = amounts) =
        PayrollComparisonEmployeeRow("PAY-001", "Frozen employee", m)

    private fun row() =
        PayrollComparisonRows(
            1,
            1,
            1,
            0,
            0,
            1,
            0,
            0,
            amounts,
            money("10.02", "0.01", "10.01"),
            listOf(
                PayrollComparisonItemRow(
                    one,
                    employee(),
                    employee(money("10.02", "0.01", "10.01")),
                    "CHANGED",
                )
            ),
        )

    private fun repo(load: () -> PayrollComparisonRows) =
        StoredPayrollComparisonRepository(
            object : PayrollComparisonDataSource {
                override fun compare(
                    company: UUID,
                    baseline: UUID,
                    current: UUID,
                    after: UUID?,
                    limit: Int,
                    changedOnly: Boolean,
                ) = load()
            }
        )

    @Test
    fun retainsExactCentDeltasAndNegativeWithholding() {
        val result = repo { row() }.compare(company, baseline, current, null, 50, false)
        assertTrue(result is Result.Success)
        val delta = (result as Result.Success).value.employees.items.single().delta
        assertEquals(BigDecimal("0.01"), delta.taxableGross)
        assertEquals(BigDecimal("0.02"), delta.withheld)
        assertEquals(BigDecimal("-0.01"), delta.takeHome)
    }

    @Test
    fun newAndDepartedEmploymentsStayDistinctWithUnsignedUuidPagination() {
        val source =
            row()
                .copy(
                    matched = 0,
                    added = 1,
                    removed = 1,
                    changed = 0,
                    items =
                        listOf(
                            PayrollComparisonItemRow(one, employee(), null, "REMOVED"),
                            PayrollComparisonItemRow(two, null, employee(), "ADDED"),
                        ),
                    current = amounts,
                )
        val result =
            repo { source }.compare(company, baseline, current, null, 1, false) as Result.Success
        assertEquals(one.toString(), result.value.employees.nextCursor)
        assertEquals(PayrollComparisonChange.REMOVED, result.value.employees.items.single().change)
        assertEquals(BigDecimal("-10.02"), result.value.employees.items.single().delta.takeHome)
        val second =
            repo { source.copy(items = source.items.drop(1)) }
                .compare(company, baseline, current, one, 1, true) as Result.Success
        assertEquals(PayrollComparisonChange.ADDED, second.value.employees.items.single().change)
        assertNull(second.value.employees.nextCursor)
    }

    @Test
    fun maximumAggregateAmountsRemainDecimalWithoutIntegerOverflow() {
        val max = money("250000000000000.00", "-3000000000000000.00", "3250000000000000.00")
        val source =
            PayrollComparisonRows(5000, 5000, 5000, 0, 0, 0, 5000, 0, max, max, emptyList())
        val result =
            repo { source }.compare(company, baseline, current, two, 50, false) as Result.Success
        assertEquals(BigDecimal("3250000000000000.00"), result.value.summary.current.takeHome)
        assertEquals(0, result.value.summary.delta.takeHome.signum())
    }

    @Test
    fun malformedCountsMoneyIdentityOrderingAndFilterFailAtTheRepositoryBoundary() {
        val r = row()
        for (source in
            listOf(
                r.copy(invalid = 1),
                r.copy(baselineCount = 5001),
                r.copy(matched = 0),
                r.copy(items = emptyList()),
                r.copy(baseline = money("50000000000.01", "0", "0")),
                r.copy(current = money("1.001", "0", "0")),
                r.copy(items = r.items + r.items),
                r.copy(items = listOf(r.items.single().copy(change = "UNCHANGED"))),
                r.copy(items = listOf(r.items.single().copy(baseline = null, current = null))),
            )) {
            assertTrue(
                repo { source }.compare(company, baseline, current, null, 50, false)
                    is Result.Failed
            )
        }
        val unchanged =
            r.copy(
                current = r.baseline,
                changed = 0,
                unchanged = 1,
                items =
                    listOf(
                        r.items
                            .single()
                            .copy(current = r.items.single().baseline, change = "UNCHANGED")
                    ),
            )
        assertTrue(
            repo { unchanged }.compare(company, baseline, current, null, 50, true) is Result.Failed
        )
        assertTrue(
            repo { error("private technical detail") }
                .compare(company, baseline, current, null, 50, false) is Result.Failed
        )
    }

    @Test
    fun cancellationPropagatesThroughAcquisitionAndMapping() {
        val failure = CancellationException("cancelled")
        assertSame(
            failure,
            assertThrows(CancellationException::class.java) {
                repo { throw failure }.compare(company, baseline, current, null, 50, false)
            },
        )
    }
}
